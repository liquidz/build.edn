(ns build-edn.pom-test
  (:require
   [build-edn.pom :as sut]
   [clojure.string :as str]
   [clojure.test :as t]))

(t/deftest generate-scm-from-git-dir-github-test
  (t/is (= {:connection "scm:git:git://github.com/liquidz/build.edn.git"
            :developerConnection "scm:git:ssh://git@github.com/liquidz/build.edn.git"
            :url "https://github.com/liquidz/build.edn"}
           (sut/generate-scm-from-git-dir)))

  (t/testing "git@"
    (with-redefs [sut/git-remote-origin-url (constantly "git@github.com:liquidz/foo.git")]
      (t/is (= {:connection "scm:git:git://github.com/liquidz/foo.git"
                :developerConnection "scm:git:ssh://git@github.com/liquidz/foo.git"
                :url "https://github.com/liquidz/foo"}
               (sut/generate-scm-from-git-dir)))))

  (t/testing "ssh"
    (with-redefs [sut/git-remote-origin-url (constantly "ssh://git@github.com/liquidz/bar.git")]
      (t/is (= {:connection "scm:git:git://github.com/liquidz/bar.git"
                :developerConnection "scm:git:ssh://git@github.com/liquidz/bar.git"
                :url "https://github.com/liquidz/bar"}
               (sut/generate-scm-from-git-dir)))))

  (t/testing "https"
    (with-redefs [sut/git-remote-origin-url (constantly "https://github.com/liquidz/baz.git")]
      (t/is (= {:connection "scm:git:git://github.com/liquidz/baz.git"
                :developerConnection "scm:git:ssh://git@github.com/liquidz/baz.git"
                :url "https://github.com/liquidz/baz"}
               (sut/generate-scm-from-git-dir))))))

(t/deftest generate-scm-from-git-dir-gitlab-test
  (with-redefs [sut/git-remote-origin-url (constantly "https://gitlab.com/gitlab-org/gitlab.git")]
    (t/is (= {:connection "scm:git:git://gitlab.com/gitlab-org/gitlab.git"
              :developerConnection "scm:git:ssh://git@gitlab.com/gitlab-org/gitlab.git"
              :url "https://gitlab.com/gitlab-org/gitlab"}
             (sut/generate-scm-from-git-dir)))))

(t/deftest generate-scm-from-git-dir-unknown-test
  (with-redefs [sut/git-remote-origin-url (constantly "https://example.com/foo/bar.git")]
    (t/is (nil? (sut/generate-scm-from-git-dir)))))

(t/deftest generate-scm-from-git-dir-failure-test
  (with-redefs [slurp (fn [& _] (throw (ex-info "test" {})))]
    (t/is (nil? (sut/generate-scm-from-git-dir)))))

(defn- normalize-pom
  [s]
  (-> s
      (str/replace #"\n\s*" "")
      (str/replace "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" "")))

(t/deftest add-description-test
  (let [dummy-pom "<project>
                     <name>dummy</name>
                   </project>"]
    (t/is (= (-> "<project>
                    <description>hello</description>
                    <name>dummy</name>
                  </project>"
                 (normalize-pom))
             (-> dummy-pom
                 (sut/add-description "hello")
                 (normalize-pom))))

    (t/testing "no duplicates"
      (t/is (= (-> "<project>
                    <description>world</description>
                    <name>dummy</name>
                  </project>"
                   (normalize-pom))
               (-> dummy-pom
                   (sut/add-description "world")
                   ;; description tag already exists
                   (sut/add-description "hello")
                   (normalize-pom)))))

    (t/testing "no name tag"
      (t/is (= "<a/>"
               (normalize-pom (sut/add-description "<a/>" "foo")))))))
