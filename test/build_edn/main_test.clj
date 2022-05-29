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
