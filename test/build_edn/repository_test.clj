(ns build-edn.repository-test
  (:require
   [build-edn.repository :as sut]
   [clojure.test :as t]
   [clojure.tools.deps.util.maven :as deps.u.maven]
   [deps-deploy.maven-settings :as dd.maven-settings])
  (:import
   (org.apache.maven.settings
    Server
    Settings)))

(defn- dummy-deps-repo-by-id
  [id]
  {id (when (= "clojars" id)
        {:id id
         :username "alice"
         :password "security"})})

(defn- dummy-get-settings
  ^Settings []
  (let [srv (doto (Server.)
              (.setId "clojars")
              (.setUsername "bob")
              (.setPassword "settings"))]
    (doto (Settings.)
      (.addServer srv))))

(defn- dummy-getenv
  [k]
  (condp = k
    "CLOJARS_USERNAME" "charlie"
    "CLOJARS_PASSWORD" "env"
    nil))

(t/deftest repository-by-id-test
  (t/testing "credentials from settings-security.xml"
    (with-redefs [sut/has-settings-security-xml? (constantly true)
                  dd.maven-settings/deps-repo-by-id dummy-deps-repo-by-id]
      (t/is (= {"clojars" {:id "clojars"
                           :username "alice"
                           :password "security"
                           :url "https://repo.clojars.org/"}}
               (sut/repository-by-id "clojars")))
      (t/is (= {"unknown" {:id "unknown"}}
               (sut/repository-by-id "unknown")))))

  (t/testing "credentials from settings.xml"
    (with-redefs [sut/has-settings-security-xml? (constantly false)
                  deps.u.maven/get-settings dummy-get-settings]
      (t/is (= {"clojars" {:id "clojars"
                           :username "bob"
                           :password "settings"
                           :url "https://repo.clojars.org/"}}
               (sut/repository-by-id "clojars")))
      (t/is (= {"unknown" {:id "unknown"}}
               (sut/repository-by-id "unknown")))))

  (t/testing "credentials from environmental variable"
    (with-redefs [sut/has-settings-security-xml? (constantly false)
                  deps.u.maven/get-settings (constantly nil)
                  sut/getenv dummy-getenv]
      (t/is (= {"clojars" {:id "clojars"
                           :username "charlie"
                           :password "env"
                           :url "https://repo.clojars.org/"}}
               (sut/repository-by-id "clojars")))))

  (t/testing "url from mvn/repos"
    (with-redefs [sut/has-settings-security-xml? (constantly false)
                  sut/deps-edn-maps (constantly {:mvn/repos {"custom" {:url "https://dummy.example.com"}}})
                  deps.u.maven/get-settings (constantly nil)
                  sut/getenv dummy-getenv]
      (t/is (= {"custom" {:id "custom"
                          :username "charlie"
                          :password "env"
                          :url "https://dummy.example.com"}}
               (sut/repository-by-id "custom"))))))
