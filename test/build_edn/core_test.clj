(ns build-edn.core-test
  (:require
   [build-edn.core :as sut]
   [build-edn.variable :as be.var]
   [clojure.string :as str]
   [clojure.test :as t]
   [clojure.tools.build.api :as b]
   [deps-deploy.deps-deploy :as deploy])
  (:import
   clojure.lang.ExceptionInfo))

(defmacro with-out-str-and-ret
  [& body]
  `(let [s# (new java.io.StringWriter)]
     (binding [*out* s#]
       (let [ret# (do ~@body)
             out# (str s#)]
         {:ret ret#
          :out (when (seq out#) out#)}))))


(t/deftest config-test
  (with-redefs [b/git-count-revs (constantly "3")
                sut/getenv #(case %
                              "CLOJARS_USERNAME" "foo"
                              "CLOJARS_PASSWORD" "bar"
                              nil)]
    (t/is (= {:lib 'foo/bar
              :version "1.2.3"
              :jar-file "target/{{lib}}.jar"
              :uber-file "target/{{lib}}-standalone.jar"
              :class-dir "target/classes"
              :deploy-repository {:url "https://clojars.org/repo"
                                  :username "foo"
                                  :password "bar"
                                  :allow-insecure-http-repository? false}
              :github-actions? false}
             (#'sut/gen-config {:lib 'foo/bar
                                :version "1.2.{{git/commit-count}}"})))

    (t/testing "scm"
      (t/is (contains? (#'sut/gen-config {:lib 'foo/bar
                                          :version "1.2.{{git/commit-count}}"
                                          :scm {:connection "a"
                                                :developerConnection "b"
                                                :url "c"}})
                       :scm))))

  (t/testing "deploy-repository"
    (t/is (= {:url "https://clojars.org/repo"
              :gpg/credential-file "dummy"
              :allow-insecure-http-repository? false}
             (-> {:lib 'foo/bar
                  :version "1.2.{{git/commit-count}}"
                  :deploy-repository {:gpg/credential-file "dummy"}}
                 (#'sut/gen-config)
                 (:deploy-repository))))))

(t/deftest pom-test
  (t/testing "normal"
    (let [write-pom-arg (atom nil)]
      (with-redefs [b/git-count-revs (constantly "3")
                    b/write-pom (fn [m] (reset! write-pom-arg m))]
        (t/is (some? (sut/pom {:lib 'foo/bar :version "1.2.{{git/commit-count}}"}))))
      (t/is (= {:lib 'foo/bar
                :version "1.2.3"
                :class-dir "target/classes"
                :src-dirs ["src"]}
               (dissoc @write-pom-arg :basis)))))

  (t/testing "github-actions?"
    (with-redefs [b/write-pom (constantly nil)]
      (let [{:keys [ret out]} (with-out-str-and-ret
                                (sut/pom {:lib 'foo/bar :version "1" :github-actions? true}))]
        (t/is (str/starts-with? out "::set-output name=pom::"))
        (t/is (str/includes? out ret)))))

  (t/testing "validation error"
    (t/is (thrown-with-msg? ExceptionInfo #"Invalid config"
            (sut/pom {})))))

(t/deftest jar-test
  (t/testing "normal"
    (let [copy-dir-arg (atom nil)
          jar-arg (atom nil)]
      (with-redefs [sut/pom (constantly nil)
                    b/git-count-revs (constantly "3")
                    b/copy-dir (fn [m] (reset! copy-dir-arg m))
                    b/jar (fn [m] (reset! jar-arg m))]
        (t/is (some? (sut/jar {:lib 'foo/bar :version "1.2.{{git/commit-count}}"}))))
      (t/is (= {:src-dirs ["src"]
                :target-dir "target/classes"}
               @copy-dir-arg))
      (t/is (= {:class-dir "target/classes"
                :jar-file "target/bar.jar"}
               @jar-arg))))

  (t/testing "github-actions?"
    (with-redefs [sut/pom (constantly nil)
                  b/copy-dir (constantly nil)
                  b/jar (constantly nil)]
      (let [{:keys [ret out]} (with-out-str-and-ret
                                (sut/jar {:lib 'foo/bar :version "1" :github-actions? true}))]
        (t/is (str/starts-with? out "::set-output name=jar::"))
        (t/is (str/includes? out ret)))))

  (t/testing "validation error"
    (t/is (thrown-with-msg? ExceptionInfo #"Invalid config"
            (sut/jar {})))))

(t/deftest uberjar-test
  (t/testing "normal"
    (let [copy-dir-arg (atom nil)
          compile-clj-arg (atom nil)
          uber-arg (atom nil)]
      (with-redefs [sut/pom (constantly nil)
                    b/git-count-revs (constantly "3")
                    b/copy-dir (fn [m] (reset! copy-dir-arg m))
                    b/compile-clj (fn [m] (reset! compile-clj-arg m))
                    b/uber (fn [m] (reset! uber-arg m))]
        (t/is (some? (sut/uberjar {:lib 'foo/bar :version "1.2.{{git/commit-count}}" :main 'bar.core}))))
      (t/is (= {:src-dirs ["src"]
                :target-dir "target/classes"}
               @copy-dir-arg))
      (t/is (= {:src-dirs ["src"]
                :class-dir "target/classes"}
               (dissoc @compile-clj-arg :basis)))
      (t/is (= {:class-dir "target/classes"
                :uber-file "target/bar-standalone.jar"
                :main 'bar.core}
               (dissoc @uber-arg :basis)))))

  (t/testing "github-actions?"
    (with-redefs [sut/pom (constantly nil)
                  b/copy-dir (constantly nil)
                  b/compile-clj (constantly nil)
                  b/uber (constantly nil)]
      (let [{:keys [ret out]} (with-out-str-and-ret
                                (sut/uberjar {:lib 'foo/bar :version "1" :main 'bar.core :github-actions? true}))]
        (t/is (str/starts-with? out "::set-output name=uberjar::"))
        (t/is (str/includes? out ret)))))

  (t/testing "validation error"
    (t/is (thrown-with-msg? ExceptionInfo #"Invalid config"
            (sut/uberjar {})))
    (t/is (thrown-with-msg? ExceptionInfo #"Invalid config"
            (sut/uberjar {:lib 'foo/bar :version "1"})))))

(t/deftest install-test
  (t/testing "normal"
    (let [deploy-arg (atom nil)]
      (with-redefs [b/git-count-revs (constantly "3")
                    sut/jar (constantly "./target/dummy.jar")
                    deploy/deploy (fn [m] (reset! deploy-arg m))]
        (t/is (some? (sut/install {:lib 'foo/bar :version "1.2.{{git/commit-count}}"}))))
      (t/is (= {:artifact "./target/dummy.jar"
                :installer :local
                :pom-file "./target/classes/META-INF/maven/foo/bar/pom.xml"}
               @deploy-arg))))

  (t/testing "github-actions?"
    (with-redefs [b/git-count-revs (constantly "3")
                  sut/jar (constantly "./target/dummy.jar")
                  deploy/deploy (constantly nil)]
      (let [{:keys [ret out]} (with-out-str-and-ret
                                (sut/install {:lib 'foo/bar :version "1.2.{{git/commit-count}}" :github-actions? true}))]
        (t/is (= out "::set-output name=version::1.2.3\n"))
        (t/is (= ret "1.2.3")))))

  (t/testing "validation error"
    (t/is (thrown-with-msg? ExceptionInfo #"Invalid config"
            (sut/install {})))))

(t/deftest deploy-test
  (t/testing "normal"
    (let [deploy-arg (atom nil)]
      (with-redefs [sut/getenv #(case %
                                  "CLOJARS_USERNAME" "alice"
                                  "CLOJARS_PASSWORD" "password"
                                  nil)
                    b/git-count-revs (constantly "3")
                    sut/jar (constantly "./target/dummy.jar")
                    deploy/deploy (fn [m] (reset! deploy-arg m))]
        (t/is (some? (sut/deploy {:lib 'foo/bar :version "1.2.{{git/commit-count}}"}))))
      (t/is (= {:artifact "./target/dummy.jar"
                :installer :remote
                :pom-file "./target/classes/META-INF/maven/foo/bar/pom.xml"
                :repository {"release" {:url "https://clojars.org/repo"
                                        :username "alice"
                                        :password "password"}}}
               @deploy-arg))))

  (t/testing "github-actions?"
    (with-redefs [sut/getenv #(case %
                                "CLOJARS_USERNAME" "alice"
                                "CLOJARS_PASSWORD" "password"
                                nil)
                  b/git-count-revs (constantly "3")
                  sut/jar (constantly "./target/dummy.jar")
                  deploy/deploy (constantly nil)]
      (let [{:keys [ret out]} (with-out-str-and-ret
                                (sut/deploy {:lib 'foo/bar :version "1.2.{{git/commit-count}}" :github-actions? true}))]
        (t/is (= out "::set-output name=version::1.2.3\n"))
        (t/is (= ret "1.2.3")))))

  (t/testing "validation error"
    (with-redefs [sut/getenv #(case %
                                "CLOJARS_USERNAME" "alice"
                                "CLOJARS_PASSWORD" "password"
                                nil)]
      (t/is (thrown-with-msg? ExceptionInfo #"Invalid config"
              (sut/deploy {}))))))

(t/deftest update-documents-test
  (t/testing "normal"
    (let [updated (atom {})]
      (with-redefs [slurp (constantly "foo\nbar\nbaz")
                    spit (fn [f content]
                           (swap! updated assoc f content))
                    be.var/current-yyyy (constantly "2112")
                    be.var/current-mm (constantly "09")
                    be.var/current-dd (constantly "03")
                    be.var/git-commit-count (constantly "3")
                    be.var/git-head-revision #(if % "short-sha" "long-sha")]
        (sut/update-documents {:lib 'foo/bar
                               :version "1.2.{{git/commit-count}}"
                               :documents [{:file "append_before.txt"
                                            :match "bar"
                                            :action :append-before
                                            :text "hello {{version}} {{now/yyyy}}-{{now/mm}}-{{now/dd}}"}
                                           {:file "append_after.txt"
                                            :match "b.r"
                                            :action :append-after
                                            :text "hello {{git/head-long-sha}}"}
                                           {:file "replace.txt"
                                            :match ".ar"
                                            :action :replace
                                            :text "hello {{git/head-short-sha}}"}]})
        (t/is (= {"append_before.txt" "foo\nhello 1.2.3 2112-09-03\nbar\nbaz"
                  "append_after.txt" "foo\nbar\nhello long-sha\nbaz"
                  "replace.txt" "foo\nhello short-sha\nbaz"}
                 @updated)))))

  (t/testing "tailing with blanks"
    (let [updated (atom {})]
      (with-redefs [slurp (constantly "foo\nbar\n")
                    spit (fn [f content]
                           (swap! updated assoc f content))]
        (sut/update-documents {:lib 'foo/bar
                               :version "1.2.{{commit-count}}"
                               :documents [{:file "foo.txt"
                                            :match "bar"
                                            :action :append-after
                                            :text "baz"}]})
        (t/is (= {"foo.txt" "foo\nbar\nbaz\n"}
                 @updated)))))

  (t/testing "validation error"
    (t/is (thrown-with-msg? ExceptionInfo #"Invalid config"
            (sut/update-documents {})))

    (t/is (thrown-with-msg? ExceptionInfo #"Invalid config"
            (sut/update-documents {:lib 'foo/bar :version "1"})))

    (t/is (thrown-with-msg? ExceptionInfo #"Invalid config"
            (sut/update-documents {:lib 'foo/bar :version "1" :documents ::invalid})))

    (t/is (thrown-with-msg? ExceptionInfo #"Invalid config"
            (sut/update-documents {:lib 'foo/bar :version "1"
                                   :documents [{}]})))

    (t/is (thrown-with-msg? ExceptionInfo #"Invalid config"
            (sut/update-documents {:lib 'foo/bar :version "1"
                                   :documents [{:file "foo"
                                                :match "bar"
                                                :action ::invalid
                                                :text "baz"}]})))))
