(ns build-edn.schema-test
  (:require
   [build-edn.schema :as sut]
   [clojure.test :as t]
   [malli.core :as m]))

(t/deftest scm-test
  (t/testing "truthy"
    (t/is (true? (m/validate sut/?scm {:connection ""
                                       :developerConnection ""
                                       :url ""}))))

  (t/testing "falsy"
    (t/is (false? (m/validate sut/?scm {})))
    (t/is (false? (m/validate sut/?scm {:connection 123
                                        :developerConnection ""
                                        :url ""})))
    (t/is (false? (m/validate sut/?scm {:connection ""
                                        :developerConnection ""})))))

(t/deftest build-config-test
  (t/testing "truthy"
    (t/is (true? (m/validate sut/?build-config {:lib 'foo/bar
                                                :version ""
                                                :description ""})))
    (t/is (true? (m/validate sut/?build-config {:lib 'foo/bar
                                                :version ""}))))

  (t/testing "falsy"
    (t/is (false? (m/validate sut/?build-config {})))
    (t/is (false? (m/validate sut/?build-config {:lib 'foo/bar
                                                 :version ""
                                                 :description 123})))))

(t/deftest java-compile-config-test
  (t/testing "'truthy"
    (t/is (true? (m/validate sut/?java-compile-config {:java-paths ["foo"]})))
    (t/is (true? (m/validate sut/?java-compile-config {:java-paths #{"foo"}})))
    (t/is (true? (m/validate sut/?java-compile-config {:java-paths []
                                                       :javac-opts ["foo"]})))
    (t/is (true? (m/validate sut/?java-compile-config {:java-paths []
                                                       :javac-opts []}))))

  (t/testing "falsy"
    (t/is (false? (m/validate sut/?java-compile-config {:java-paths [123]})))
    (t/is (false? (m/validate sut/?java-compile-config {:java-paths []
                                                        :javac-opts [123]})))))

(t/deftest uber-build-config-test
  (t/testing "truthy"
    (t/is (true? (m/validate sut/?uber-build-config {:uber-file ""
                                                     :main 'foo
                                                     :skip-compiling-dirs []})))
    (t/is (true? (m/validate sut/?uber-build-config {:uber-file ""
                                                     :main 'foo
                                                     :skip-compiling-dirs #{}})))
    (t/is (true? (m/validate sut/?uber-build-config {:uber-file ""
                                                     :main nil}))))

  (t/testing "falsy"
    (t/is (false? (m/validate sut/?uber-build-config {})))
    (t/is (false? (m/validate sut/?uber-build-config {:uber-file 123
                                                      :main 'foo})))
    (t/is (false? (m/validate sut/?uber-build-config {:uber-file ""
                                                      :main 'foo
                                                      :skip-compiling-dirs {}})))))

(t/deftest document-test
  (t/testing "truthy"
    (t/is (true? (m/validate sut/?document {:file ""
                                            :match ""
                                            :action :replace
                                            :text ""})))
    (t/is (true? (m/validate sut/?document {:file ""
                                            :action :create
                                            :text ""})))
    (t/is (true? (m/validate sut/?document {:file ""
                                            :match ""
                                            :action :append-after
                                            :keep-indent? true
                                            :text ""}))))

  (t/testing "falsy"
    (t/is (false? (m/validate sut/?document {})))
    (t/is (false? (m/validate sut/?document {:file ""
                                             :action :replace
                                             :text ""})))
    (t/is (false? (m/validate sut/?document {:file 123
                                             :match ""
                                             :action :replace
                                             :text ""})))
    (t/is (false? (m/validate sut/?document {:file ""
                                             :action :create
                                             :keep-indent? true
                                             :text ""})))))

(t/deftest documents-build-config-test
  (t/testing "truthy"
    (t/is (true? (m/validate sut/?documents-build-config {:documents []})))
    (t/is (true? (m/validate sut/?documents-build-config {:documents [{:file ""
                                                                       :action :create
                                                                       :text ""}]}))))

  (t/testing "falsy"
    (t/is (false? (m/validate sut/?documents-build-config {})))
    (t/is (false? (m/validate sut/?documents-build-config {:documents "foo"})))))

(t/deftest deploy-repository-test
  (t/testing "truthy"
    (t/is (true? (m/validate sut/?deploy-repository {:id ""})))
    (t/is (true? (m/validate sut/?deploy-repository {:id ""
                                                     :username ""
                                                     :password ""
                                                     :url ""}))))

  (t/testing "falsy"
    (t/is (false? (m/validate sut/?deploy-repository {})))
    (t/is (false? (m/validate sut/?deploy-repository {:id ""
                                                      :username 123})))))

(t/deftest deploy-repository-build-config-test
  (t/testing "truthy"
    (t/is (true? (m/validate sut/?deploy-repository-build-config {:deploy-repository {:id ""}}))))

  (t/testing "falsy"
    (t/is (false? (m/validate sut/?deploy-repository-build-config {})))
    (t/is (false? (m/validate sut/?deploy-repository-build-config {:deploy-repository {}})))
    (t/is (false? (m/validate sut/?deploy-repository-build-config {:deploy-repository "foo"})))))
