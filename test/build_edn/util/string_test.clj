(ns build-edn.util.string-test
  (:require
   [build-edn.util.string :as sut]
   [clojure.test :as t]))

(t/deftest add-indent-test
  (t/is (= "" (sut/add-indent "" "")))
  (t/is (= "foo" (sut/add-indent "" "foo")))
  (t/is (= "foo" (sut/add-indent "bar" "foo")))
  (t/is (= "  foo" (sut/add-indent "  bar" "foo")))
  (t/is (= "    foo" (sut/add-indent "  bar" "  foo")))
  (t/is (= "\n  foo" (sut/add-indent "  bar" "\nfoo"))))
