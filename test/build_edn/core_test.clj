(ns build-edn.core-test
  (:require
   [build-edn.core :as sut]
   [clojure.test :as t]
   [clojure.tools.build.api :as b]))

(t/deftest config-test
  (with-redefs [b/git-count-revs (constantly "3")]
    (t/is (= {:lib 'foo/bar
              :version "1.2.3"
              :changelog-title-format "## {{version}} ({{date}})"
              :jar-file "target/bar.jar"
              :uber-file "target/bar-standalone.jar"
              :class-dir "target/classes"
              :changelog-file "CHANGELOG.md"
              :unreleased-title "Unreleased"
              :github-action? false}
             (#'sut/gen-config {:lib 'foo/bar
                                :version "1.2.{{commit-count}}"})))

    (t/testing "scm"
      (t/is (contains? (#'sut/gen-config {:lib 'foo/bar
                                          :version "1.2.{{commit-count}}"
                                          :scm {:connection "a"
                                                :developerConnection "b"
                                                :url "c"}})
                       :scm)))))
