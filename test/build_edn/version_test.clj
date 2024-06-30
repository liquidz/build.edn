(ns build-edn.version-test
  (:require
   [build-edn.test-helper :as h]
   [build-edn.version :as sut]
   [clojure.test :as t]))

(t/use-fixtures :once h/malli-instrument-fixture)

(t/deftest parse-semantic-version-test
  (t/is (= {:version/major "1"
            :version/minor "2"
            :version/patch "3"
            :version/snapshot? false}
           (sut/parse-semantic-version "1.2.3")))
  (t/is (= {:version/major "2112"
            :version/minor "09"
            :version/patch "03"
            :version/snapshot? false}
           (sut/parse-semantic-version "2112.09.03")))
  (t/is (= {:version/major "4"
            :version/minor "5"
            :version/patch "6"
            :version/snapshot? true}
           (sut/parse-semantic-version "4.5.6-SNAPSHOT")))
  (t/is (= {:version/major "7"
            :version/minor "8"
            :version/patch "{{git/commit-count}}"
            :version/snapshot? false}
           (sut/parse-semantic-version "7.8.{{git/commit-count}}")))

  (t/is (nil? (sut/parse-semantic-version "1.2")))
  (t/is (nil? (sut/parse-semantic-version "")))
  (t/is (nil? (sut/parse-semantic-version nil))))

(t/deftest to-semantic-version-test
  (t/is (= "1.2.3"
           (sut/to-semantic-version {:version/major "1"
                                     :version/minor "2"
                                     :version/patch "3"
                                     :version/snapshot? false})))

  (t/is (= "4.5.6-SNAPSHOT"
           (sut/to-semantic-version {:version/major "4"
                                     :version/minor "5"
                                     :version/patch "6"
                                     :version/snapshot? true}))))

(t/deftest bump-version-test
  (let [v (sut/parse-semantic-version "1.2.3")]
    (t/testing "major"
      (t/is (= "2.0.0"
               (-> (sut/bump-version v :major)
                   (sut/to-semantic-version)))))
    (t/testing "minor"
      (t/is (= "1.3.0"
               (-> (sut/bump-version v :minor)
                   (sut/to-semantic-version)))))
    (t/testing "patch"
      (t/is (= "1.2.4"
               (-> (sut/bump-version v)
                   (sut/to-semantic-version))
               (-> (sut/bump-version v :patch)
                   (sut/to-semantic-version))))))

  (let [v (sut/parse-semantic-version "1.2.{{git/commit-count}}")]
    (t/is (nil? (sut/bump-version v)))
    (t/testing "minor"
      (t/is (= "1.3.{{git/commit-count}}"
               (-> (sut/bump-version v :minor)
                   (sut/to-semantic-version)))))))

(t/deftest add-snapshot-test
  (let [v (sut/parse-semantic-version "1.2.3")]
    (t/is (= "1.2.3-SNAPSHOT"
             (-> v
                 (sut/add-snapshot)
                 (sut/to-semantic-version))))))

(t/deftest remove-snapshot-test
  (let [v (sut/parse-semantic-version "1.2.3-SNAPSHOT")]
    (t/is (= "1.2.3"
             (-> v
                 (sut/remove-snapshot)
                 (sut/to-semantic-version))))))
