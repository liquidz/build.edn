(ns build-edn.core-test
  (:require
   [build-edn.core :as sut]
   [build-edn.pom :as be.pom]
   [build-edn.repository :as be.repo]
   [build-edn.variable :as be.var]
   [clojure.test :as t]
   [clojure.tools.build.api :as b]
   [deps-deploy.deps-deploy :as deploy])
  (:import
   clojure.lang.ExceptionInfo))

(def ^:private dummy-githut-output
  "__dummy-github-output__")

(defn- dummy-getenv
  [k]
  (case k
    "GITHUB_OUTPUT" dummy-githut-output
    nil))

(t/deftest config-test
  (with-redefs [b/git-count-revs (constantly "3")]
    (t/is (= {:lib 'foo/bar
              :version "1.2.3"
              :jar-file "target/{{lib}}.jar"
              :uber-file "target/{{lib}}-standalone.jar"
              :class-dir "target/classes"
              :deploy-repository {:id "clojars"}
              :scm {:connection "scm:git:git://github.com/liquidz/build.edn.git"
                    :developerConnection "scm:git:ssh://git@github.com/liquidz/build.edn.git"
                    :url "https://github.com/liquidz/build.edn"
                    :tag "1.2.3"}
              :skip-compiling-dirs #{"resources"}
              :pom {:no-clojure-itself? false}
              :github-actions? false}
             (#'sut/gen-config {:lib 'foo/bar
                                :version "1.2.{{git/commit-count}}"})))

    (t/testing "custom scm"
      (t/is (= {:connection "a"
                :developerConnection "b"
                :url "c"
                :tag "1.2.3"}
               (:scm (#'sut/gen-config {:lib 'foo/bar
                                        :version "1.2.{{git/commit-count}}"
                                        :pom {:scm {:connection "a"
                                                    :developerConnection "b"
                                                    :url "c"}}})))))

    (t/testing "failed to generate scm"
      (with-redefs [be.pom/generate-scm-from-git-dir (constantly nil)]
        (t/is (not (contains? (#'sut/gen-config {:lib 'foo/bar
                                                 :version "1.2.{{git/commit-count}}"})
                              :scm)))))))

(t/deftest pom-test
  (t/testing "normal"
    (let [write-pom-arg (atom nil)]
      (with-redefs [b/git-count-revs (constantly "3")
                    b/write-pom (fn [m] (reset! write-pom-arg m))]
        (t/is (some? (sut/pom {:lib 'foo/bar :version "1.2.{{git/commit-count}}"}))))
      (t/is (= {:lib 'foo/bar
                :version "1.2.3"
                :class-dir "target/classes"
                :src-dirs ["src"]
                :scm {:connection "scm:git:git://github.com/liquidz/build.edn.git"
                      :developerConnection "scm:git:ssh://git@github.com/liquidz/build.edn.git"
                      :url "https://github.com/liquidz/build.edn"
                      :tag "1.2.3"}
                :pom-data []}
               (dissoc @write-pom-arg :basis)))

      (t/is (contains? (get-in @write-pom-arg [:basis :libs])
                       'org.clojure/clojure))))

  (t/testing "source-dirs"
    (let [write-pom-arg (atom nil)]
      (with-redefs [b/write-pom (fn [m] (reset! write-pom-arg m))]
        (t/is (some? (sut/pom {:lib 'foo/bar
                               :version "1.2.3"
                               :source-dirs ["foo" "bar"]}))))
      (t/is (= ["foo" "bar"]
               (:src-dirs @write-pom-arg)))))

  (t/testing "no-clojure-itself?"
    (let [write-pom-arg (atom nil)]
      (with-redefs [b/write-pom (fn [m] (reset! write-pom-arg m))]
        (t/is (some? (sut/pom {:lib 'foo/bar
                               :version "1.2.3"
                               :pom {:no-clojure-itself? true}}))))
      (t/is (not (contains? (get-in @write-pom-arg [:basis :libs])
                            'org.clojure/clojure)))))

  (t/testing "github-actions?"
    (let [output (atom {})]
      (with-redefs [b/write-pom (constantly nil)
                    sut/getenv dummy-getenv
                    spit (fn [f content & _options]
                           (swap! output assoc f content))]
        (let [ret (sut/pom {:lib 'foo/bar :version "1" :github-actions? true})]
          (t/is (seq ret))
          (t/is (= {dummy-githut-output (format "pom=%s\n" ret)}
                   @output))))))

  (t/testing "description"
    (let [write-pom-arg (atom nil)]
      (with-redefs [b/write-pom (fn [m] (reset! write-pom-arg m))]
        (t/is (some? (sut/pom {:lib 'foo/bar
                               :version "1.2.3"
                               :description "hello world"}))))
      (t/is (= [[:description "hello world"]]
               (:pom-data @write-pom-arg)))))

  (t/testing "licenses"
    (let [write-pom-arg (atom nil)]
      (with-redefs [b/write-pom (fn [m] (reset! write-pom-arg m))]
        (t/is (some? (sut/pom {:lib 'foo/bar
                               :version "1.2.3"
                               :licenses [{:name "dummy license1" :url "https://dummy1.example.com"}
                                          {:name "dummy license2" :url "https://dummy2.example.com"}]}))))
      (t/is (= [[:licenses
                 [:license
                  [:name "dummy license1"]
                  [:url "https://dummy1.example.com"]]
                 [:license
                  [:name "dummy license2"]
                  [:url "https://dummy2.example.com"]]]]
               (:pom-data @write-pom-arg)))))

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

  (t/testing "source-dirs"
    (let [copy-dir-arg (atom nil)]
      (with-redefs [sut/pom (constantly nil)
                    b/copy-dir (fn [m] (reset! copy-dir-arg m))
                    b/jar (constantly nil)]
        (t/is (some? (sut/jar {:lib 'foo/bar
                               :version "1.2.3"
                               :source-dirs ["foo" "bar"]}))))
      (t/is (= {:src-dirs ["foo" "bar"]
                :target-dir "target/classes"}
               @copy-dir-arg))))

  (t/testing "github-actions?"
    (let [output (atom {})]
      (with-redefs [sut/pom (constantly nil)
                    b/copy-dir (constantly nil)
                    b/jar (constantly nil)
                    sut/getenv dummy-getenv
                    spit (fn [f contents & _]
                           (swap! output assoc f contents))]
        (let [ret (sut/jar {:lib 'foo/bar :version "1" :github-actions? true})]
          (t/is (seq ret))
          (t/is (= {dummy-githut-output (format "jar=%s\n" ret)}
                   @output))))))

  (t/testing "validation error"
    (t/is (thrown-with-msg? ExceptionInfo #"Invalid config"
            (sut/jar {})))))

(t/deftest java-compile-test
  (t/testing "normal"
    (let [javac-arg (atom nil)]
      (with-redefs [sut/get-basis (constantly {:foo 1})
                    b/javac (fn [m] (reset! javac-arg m))]
        (t/is (some? (sut/java-compile {:lib 'foo/bar
                                        :version "1.2.{{git/commit-count}}"
                                        :java-paths ["src-java"]}))))
      (t/is (= {:src-dirs ["src-java"]
                :class-dir "target/classes"
                :basis {:foo 1}
                :javac-opts nil}
               @javac-arg))))

  (t/testing "javac-opts"
    (let [javac-arg (atom nil)]
      (with-redefs [sut/get-basis (constantly {:foo 1})
                    b/javac (fn [m] (reset! javac-arg m))]
        (t/is (some? (sut/java-compile {:lib 'foo/bar
                                        :version "1.2.{{git/commit-count}}"
                                        :java-paths ["src-java"]
                                        :javac-opts ["-Xlint:-options"]}))))
      (t/is (= {:src-dirs ["src-java"]
                :class-dir "target/classes"
                :basis {:foo 1}
                :javac-opts ["-Xlint:-options"]}
               @javac-arg))))

  (t/testing "validation error"
    (t/is (thrown-with-msg? ExceptionInfo #"Invalid config"
            (sut/java-compile {})))
    (t/is (thrown-with-msg? ExceptionInfo #"Invalid config"
            (sut/java-compile {:lib 'foo/bar :version "1"})))))

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
        (t/is (some? (sut/uberjar {:lib 'foo/bar
                                   :version "1.2.{{git/commit-count}}"
                                   :main 'bar.core}))))
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

  (t/testing "source-dirs"
    (let [copy-dir-arg (atom nil)
          compile-clj-arg (atom nil)]
      (with-redefs [sut/pom (constantly nil)
                    b/copy-dir (fn [m] (reset! copy-dir-arg m))
                    b/compile-clj (fn [m] (reset! compile-clj-arg m))
                    b/uber (constantly nil)]
        (t/is (some? (sut/uberjar {:lib 'foo/bar
                                   :version "1.2.3"
                                   :source-dirs ["foo" "bar"]
                                   :main 'bar.core}))))
      (t/is (= {:src-dirs ["foo" "bar"]
                :target-dir "target/classes"}
               @copy-dir-arg))
      (t/is (= {:src-dirs ["foo" "bar"]
                :class-dir "target/classes"}
               (dissoc @compile-clj-arg :basis)))))

  (t/testing "github-actions?"
    (let [output (atom {})]
      (with-redefs [sut/pom (constantly nil)
                    b/copy-dir (constantly nil)
                    b/compile-clj (constantly nil)
                    b/uber (constantly nil)
                    sut/getenv dummy-getenv
                    spit (fn [f contents & _]
                           (swap! output assoc f contents))]
        (let [ret (sut/uberjar {:lib 'foo/bar :version "1" :main 'bar.core :github-actions? true})]
          (t/is (seq ret))
          (t/is (= {dummy-githut-output (format "uberjar=%s\n" ret)}
                   @output))))))

  (t/testing "skip-compiling-dirs"
    (let [compile-clj-arg (atom nil)]
      (with-redefs [sut/pom (constantly nil)
                    b/copy-dir (constantly nil)
                    b/compile-clj (fn [m] (reset! compile-clj-arg m))
                    b/uber (constantly nil)]
        (t/is (some? (sut/uberjar {:lib 'bar/baz
                                   :version "1.0.0"
                                   :main 'baz.core
                                   :skip-compiling-dirs #{"src"}}))))
      (t/is (= [] (:src-dirs @compile-clj-arg)))))

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
    (let [output (atom {})]
      (with-redefs [b/git-count-revs (constantly "3")
                    sut/jar (constantly "./target/dummy.jar")
                    deploy/deploy (constantly nil)
                    sut/getenv dummy-getenv
                    spit (fn [f contents & _]
                           (swap! output assoc f contents))]
        (t/is (= "1.2.3"
                 (sut/install {:lib 'foo/bar :version "1.2.{{git/commit-count}}" :github-actions? true})))
        (t/is (= {dummy-githut-output "version=1.2.3\n"}
                 @output)))))

  (t/testing "validation error"
    (t/is (thrown-with-msg? ExceptionInfo #"Invalid config"
            (sut/install {})))))

(be.repo/repository-by-id "neko")
(defn- dummy-repository-by-id
  [id]
  {id (merge {:id id}
             (when (= "clojars" id)
               {:username "alice"
                :password "password"
                :url "https://repo.clojars.org/"}))})

(t/deftest deploy-test
  (t/testing "normal"
    (let [deploy-arg (atom nil)]
      (with-redefs [be.repo/repository-by-id dummy-repository-by-id
                    b/git-count-revs (constantly "3")
                    sut/jar (constantly "./target/dummy.jar")
                    deploy/deploy (fn [m] (reset! deploy-arg m))]
        (t/is (some? (sut/deploy {:lib 'foo/bar :version "1.2.{{git/commit-count}}"}))))
      (t/is (= {:artifact "./target/dummy.jar"
                :installer :remote
                :pom-file "./target/classes/META-INF/maven/foo/bar/pom.xml"
                :repository {"clojars" {:id "clojars"
                                        :username "alice"
                                        :password "password"
                                        :url "https://repo.clojars.org/"}}}
               @deploy-arg))))

  (t/testing "github-actions?"
    (let [output (atom {})]
      (with-redefs [be.repo/repository-by-id dummy-repository-by-id
                    b/git-count-revs (constantly "3")
                    sut/jar (constantly "./target/dummy.jar")
                    deploy/deploy (constantly nil)
                    sut/getenv dummy-getenv
                    spit (fn [f contents & _]
                           (swap! output assoc f contents))]
        (t/is (= "1.2.3"
                 (sut/deploy {:lib 'foo/bar :version "1.2.{{git/commit-count}}" :github-actions? true})))
        (t/is (= {dummy-githut-output "version=1.2.3\n"}
                 @output)))))

  (t/testing "version fixed"
    (let [output (atom {})]
      (with-redefs [be.repo/repository-by-id dummy-repository-by-id
                    sut/jar (constantly "./target/dummy.jar")
                    deploy/deploy (constantly nil)
                    sut/getenv dummy-getenv
                    spit (fn [f contents & _]
                           (swap! output assoc f contents))]
        (t/is (= "9.8.7"
                 (sut/deploy {:lib 'foo/bar :version "9.8.7" :github-actions? true})))
        (t/is (= {dummy-githut-output "version=9.8.7\n"}
                 @output)))))

  (t/testing "validation error"
    (with-redefs [be.repo/repository-by-id dummy-repository-by-id]
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
                                            :text "hello {{git/head-short-sha}}"}
                                           {:file "version.txt"
                                            :match "bar"
                                            :action :replace
                                            :text "{{version/patch}}/{{version/minor}}/{{version/major}}"}
                                           {:file "create.txt"
                                            :action :create
                                            :text "created-{{version}}"}]})
        (t/is (= {"append_before.txt" "foo\nhello 1.2.3 2112-09-03\nbar\nbaz"
                  "append_after.txt" "foo\nbar\nhello long-sha\nbaz"
                  "replace.txt" "foo\nhello short-sha\nbaz"
                  "version.txt" "foo\n3/2/1\nbaz"
                  "create.txt" "created-1.2.3"}
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

  (t/testing "match-exactly"
    (let [updated (atom {})]
      (with-redefs [slurp (constantly "a_b_c\na.b.c\n")
                    spit (fn [f content]
                           (swap! updated assoc f content))]
        (sut/update-documents {:lib 'foo/bar
                               :version "1.2.{{commit-count}}"
                               :documents [{:file "foo.txt"
                                            :match-exactly "a.b.c"
                                            :action :replace
                                            :text "baz"}]})
        (t/is (= {"foo.txt" "a_b_c\nbaz\n"}
                 @updated)))))

  (t/testing "keep-indent?"
    (let [updated (atom {})]
      (with-redefs [slurp (constantly "foo\n  bar")
                    spit (fn [f content]
                           (swap! updated assoc f content))]
        (sut/update-documents {:lib 'foo/bar
                               :version "1.2.{{commit-count}}"
                               :documents [{:file "append_before.txt"
                                            :match "bar"
                                            :action :append-before
                                            :keep-indent? true
                                            :text "baz"}
                                           {:file "replace.txt"
                                            :match "bar"
                                            :action :replace
                                            :keep-indent? true
                                            :text "baz"}
                                           {:file "append_after.txt"
                                            :match "bar"
                                            :action :append-after
                                            :keep-indent? true
                                            :text "baz"}]})
        (t/is (= {"append_before.txt" "foo\n  baz\n  bar"
                  "replace.txt" "foo\n  baz"
                  "append_after.txt" "foo\n  bar\n  baz"}
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

(t/deftest lint-test
  (let [lint' #(with-redefs [sut/print-error (constantly nil)
                             println (constantly nil)]
                 (sut/lint (merge {:lib 'foo/bar :version "1"} %)))]
    (t/is (true? (lint' {})))
    (t/is (false? (lint' {:lib "invalid"})))

    (t/testing "uberjar"
      (t/is (true? (lint' {:main 'foo})))
      (t/is (true? (lint' {:main 'foo :skip-compiling-dirs []})))
      (t/is (true? (lint' {:main 'foo :skip-compiling-dirs #{}})))
      (t/is (false? (lint' {:main "invalid"})))
      (t/is (false? (lint' {:main 'foo :skip-compiling-dirs {}}))))

    (t/testing "documents"
      (t/is (true? (lint' {:documents []})))
      (t/is (false? (lint' {:documents "invalid"}))))

    (t/testing "deploy-repository"
      (t/is (true? (lint' {:deploy-repository {:id "foo"}})))
      (t/is (false? (lint' {:deploy-repository {}})))
      (t/is (false? (lint' {:deploy-repository {:id 123}}))))

    (t/testing "pom"
      (t/is (true? (lint' {:pom {}})))
      (t/is (true? (lint' {:pom {:no-clojure-itself? true}})))
      (t/is (true? (lint' {:pom {:scm {:connection "foo"
                                       :developerConnection "bar"
                                       :url "baz"}}})))
      (t/is (false? (lint' {:pom {:no-clojure-itself? "invalid"}})))
      (t/is (false? (lint' {:pom {:scm "invalid"}})))
      (t/is (false? (lint' {:pom {:scm {:invalid "foo"}}}))))

    (t/testing "java-compile"
      (t/is (true? (lint' {:java-paths []})))
      (t/is (true? (lint' {:java-paths []
                           :javac-opts []})))
      (t/is (false? (lint' {:java-paths [123]})))
      (t/is (false? (lint' {:java-paths []
                            :javac-opts [123]}))))))
