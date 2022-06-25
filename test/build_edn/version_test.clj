(ns build-edn.version-test
  (:require
   [build-edn.version :as sut]
   [clojure.test :as t]))

(t/deftest parse-semantic-version-test
  (t/is (= {:version/major "1"
            :version/minor "2"
            :version/patch "3"}
           (sut/parse-semantic-version "1.2.3")))
  (t/is (= {:version/major "2112"
            :version/minor "09"
            :version/patch "03"}
           (sut/parse-semantic-version "2112.09.03")))

  (t/is (nil? (sut/parse-semantic-version "1.2.3.4")))
  (t/is (nil? (sut/parse-semantic-version "1.2")))
  (t/is (nil? (sut/parse-semantic-version "")))
  (t/is (nil? (sut/parse-semantic-version nil))))
