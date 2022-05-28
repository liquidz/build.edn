(ns build-edn.gpg-test
  (:require
   [build-edn.gpg :as sut]
   [clojure.test :as t])
  (:import clojure.lang.ExceptionInfo))

(t/deftest get-credential-by-repository-test
  (with-redefs [sut/gpg-decrypt #(if (= "dummy" %)
                                   (pr-str {#"localhost"
                                            {:username "alice"
                                             :password "password"}})
                                   (throw (ex-info "No such file or directory" {})))]
    (t/is (= {:username "alice" :password "password"}
             (sut/get-credential-by-repository
              {:url "http://localhost/"
               :gpg/credential-file "dummy"})))

    (t/is (thrown-with-msg? AssertionError #"must contain a setting for"
            (sut/get-credential-by-repository
             {:url "http://unknown/"
              :gpg/credential-file "dummy"})))

    (t/is (thrown-with-msg? ExceptionInfo #"No such file"
            (sut/get-credential-by-repository
             {:url "http://localhost"
              :gpg/credential-file "invalid"}))))

  (t/testing "invalid decrypted"
    (with-redefs [sut/gpg-decrypt (constantly
                                   (pr-str {#"localhost"
                                            {:username "alice"}}))]
      (t/is (thrown-with-msg? AssertionError #"must contain :password"
              (sut/get-credential-by-repository
               {:url "http://localhost"
                :gpg/credential-file "dummy"}))))

    (with-redefs [sut/gpg-decrypt (constantly
                                   (pr-str {#"localhost"
                                            {:password "password"}}))]
      (t/is (thrown-with-msg? AssertionError #"must contain :user"
              (sut/get-credential-by-repository
               {:url "http://localhost"
                :gpg/credential-file "dummy"}))))))
