(ns build-edn.core-test
  (:require
   [build-edn.core :as sut]
   [clojure.test :as t]
   [clojure.tools.build.api :as b]))

(t/deftest config-test
  (with-redefs [b/git-count-revs (constantly "3")]
    (t/is (= {:lib 'foo/bar
              :version "1.2.3"
              :jar-file "target/bar.jar"
              :uber-file "target/bar-standalone.jar"
              :class-dir "target/classes"
              :github-actions? false}
             (#'sut/gen-config {:lib 'foo/bar
                                :version "1.2.{{commit-count}}"})))

    (t/testing "scm"
      (t/is (contains? (#'sut/gen-config {:lib 'foo/bar
                                          :version "1.2.{{commit-count}}"
                                          :scm {:connection "a"
                                                :developerConnection "b"
                                                :url "c"}})
                       :scm)))))

(t/deftest update-documents-test
  (let [updated (atom {})]
    (with-redefs [slurp (constantly "foo\nbar\nbaz")
                  spit (fn [f content]
                         (swap! updated assoc f content))
                  sut/git-commit-count (constantly "3")
                  sut/git-head-revision #(if % "short-sha" "long-sha")]
      (sut/update-documents {:lib 'foo/bar
                             :version "1.2.{{commit-count}}"
                             :documents [{:file "append_before.txt"
                                          :match "bar"
                                          :action :append-before
                                          :text "hello {{version}} {{yyyy-mm-dd}}"}
                                         {:file "append_after.txt"
                                          :match "b.r"
                                          :action :append-after
                                          :text "hello {{git-head-long-sha}}"}
                                         {:file "replace.txt"
                                          :match ".ar"
                                          :action :replace
                                          :text "hello {{git-head-short-sha}}"}]})
      (t/is (= {"append_before.txt" "foo\nhello 1.2.3\nbar\nbaz"
                "append_after.txt" "foo\nbar\nhello long-sha\nbaz"
                "replace.txt" "foo\nhello short-sha\nbaz"}
               @updated)))))
