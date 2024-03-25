(ns build-edn.repository
  (:require
   [clojure.java.io :as io]
   [clojure.tools.deps :as deps]
   [clojure.tools.deps.util.maven :as deps.u.maven]
   [deps-deploy.maven-settings :as dd.maven-settings])
  (:import
   org.apache.maven.settings.Server))

(def default-username-env "CLOJARS_USERNAME")
(def default-password-env "CLOJARS_PASSWORD")

(defn- getenv
  [k]
  (System/getenv k))

(defn- has-settings-security-xml?
  []
  (.exists (io/file dd.maven-settings/default-settings-security-path)))

(defn- credential-by-id
  [server-id]
  (if (has-settings-security-xml?)
    (get (dd.maven-settings/deps-repo-by-id server-id)
         server-id)
    (if-let [server (some->> (deps.u.maven/get-settings)
                             (.getServers)
                             (some #(and (= server-id (.getId ^Server %)) %)))]
      {:username (.getUsername server)
       :password (.getPassword server)}
      (let [username (getenv default-username-env)
            password (getenv default-password-env)]
        (cond-> {}
          username (assoc :username username)
          password (assoc :password password))))))

(defn- deps-edn-maps
  []
  (let [{:keys [root-edn user-edn project-edn]} (deps/find-edn-maps)]
    (deps/merge-edns [root-edn user-edn project-edn])))

(defn- url-by-id
  [server-id]
  (-> deps.u.maven/standard-repos
      (merge (:mvn/repos (deps-edn-maps)))
      (get server-id {})))

(defn repository-by-id
  [server-id]
  {server-id (merge {:id server-id}
                    (credential-by-id server-id)
                    (url-by-id server-id))})
