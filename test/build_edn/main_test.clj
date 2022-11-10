(ns build-edn.main-test
  (:require
   [build-edn.core :as core]
   [build-edn.main :as sut]
   [clojure.test :as t]))

(t/deftest pom-test
  (with-redefs [core/pom identity]
    (t/is (= 'com.github.liquidz/build.edn
             (:lib (sut/pom {}))))
    (t/is (= 'foo/bar
             (:lib (sut/pom {:lib 'foo/bar}))))))

(t/deftest jar-test
  (with-redefs [core/jar identity]
    (t/is (= 'com.github.liquidz/build.edn
             (:lib (sut/jar {}))))))

(t/deftest uberjar-test
  (with-redefs [core/uberjar identity]
    (t/is (= 'com.github.liquidz/build.edn
             (:lib (sut/uberjar {}))))))

(t/deftest install-test
  (with-redefs [core/install identity]
    (t/is (= 'com.github.liquidz/build.edn
             (:lib (sut/install {}))))))

(t/deftest deploy-test
  (with-redefs [core/deploy identity]
    (t/is (= 'com.github.liquidz/build.edn
             (:lib (sut/deploy {}))))))

(t/deftest update-documents-test
  (with-redefs [core/update-documents identity]
    (t/is (= 'com.github.liquidz/build.edn
             (:lib (sut/update-documents {}))))))

(t/deftest lint-test
  (with-redefs [core/lint identity]
    (t/is (= 'com.github.liquidz/build.edn
             (:lib (sut/lint {}))))))

(t/deftest bump-patch-version-test
  (with-redefs [core/bump-version list]
    (let [[arg version-type] (sut/bump-patch-version {})]
      (t/is (= 'com.github.liquidz/build.edn (:lib arg)))
      (t/is (= :patch version-type)))))

(t/deftest bump-minor-version-test
  (with-redefs [core/bump-version list]
    (let [[arg version-type] (sut/bump-minor-version {})]
      (t/is (= 'com.github.liquidz/build.edn (:lib arg)))
      (t/is (= :minor version-type)))))

(t/deftest bump-major-version-test
  (with-redefs [core/bump-version list]
    (let [[arg version-type] (sut/bump-major-version {})]
      (t/is (= 'com.github.liquidz/build.edn (:lib arg)))
      (t/is (= :major version-type)))))

(t/deftest add-snapshot-test
  (with-redefs [core/add-snapshot identity]
    (t/is (= 'com.github.liquidz/build.edn
             (:lib (sut/add-snapshot {}))))))

(t/deftest remove-snapshot-test
  (with-redefs [core/remove-snapshot identity]
    (t/is (= 'com.github.liquidz/build.edn
             (:lib (sut/remove-snapshot {}))))))

(t/deftest execute-test
  (t/testing "normal"
    (let [bump-version-arg (atom nil)
          deploy-arg (atom nil)]
      (with-redefs [core/bump-version (fn [& x] (reset! bump-version-arg (vec x)))
                    core/deploy (fn [m] (reset! deploy-arg m))]
        (sut/execute {:fns '[bump-patch-version deploy]})
        (t/is (= ['com.github.liquidz/build.edn :patch]
                 (update @bump-version-arg 0 :lib)))
        (t/is (= 'com.github.liquidz/build.edn
                 (:lib @deploy-arg))))))

  (t/testing "unknown function"
    (let [bump-version-arg (atom nil)
          deploy-arg (atom nil)]
      (with-redefs [core/bump-version (fn [& _] (reset! bump-version-arg true))
                    core/deploy (fn [& _] (reset! deploy-arg true))]
        (t/is (= "Failed to reolve build-edn.main/unknown.\n"
                 (with-out-str
                   (sut/execute {:fns '[bump-patch-version unknown deploy]}))))
        (t/is (nil? @bump-version-arg))
        (t/is (nil? @deploy-arg)))))

  (t/testing "execute in execute"
    (let [bump-version-arg (atom nil)
          deploy-arg (atom nil)]
      (with-redefs [core/bump-version (fn [& _] (reset! bump-version-arg true))
                    core/deploy (fn [& _] (reset! deploy-arg true))]
        (t/is (= "Could not use 'execute' in 'execute'.\n"
                 (with-out-str
                   (sut/execute {:fns '[bump-patch-version execute deploy]}))))
        (t/is (nil? @bump-version-arg))
        (t/is (nil? @deploy-arg))))))
