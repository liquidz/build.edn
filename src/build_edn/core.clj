(ns build-edn.core
  (:require
   [build-edn.gpg :as be.gpg]
   [build-edn.schema :as be.schema]
   [build-edn.variable :as be.var]
   [cemerick.pomegranate.aether :as aether]
   [clojure.string :as str]
   [clojure.tools.build.api :as b]
   [deps-deploy.deps-deploy :as deploy]
   [malli.core :as m]
   [malli.error :as me]
   [malli.util :as mu]
   [pogonos.core :as pg])
  (:import
   org.apache.maven.wagon.providers.http.HttpWagon))

(defn- getenv
  [k]
  (System/getenv k))

(defn- gen-default-configs
  []
  {:class-dir "target/classes"
   :jar-file "target/{{lib}}.jar"
   :uber-file "target/{{lib}}-standalone.jar"
   :deploy-repository (let [username (getenv "CLOJARS_USERNAME")
                            password (getenv "CLOJARS_PASSWORD")]
                        (cond-> {:url "https://clojars.org/repo"
                                 :allow-insecure-http-repository? false}
                          username (assoc :username username)
                          password (assoc :password password)))
   :github-actions? false})

(defn- validate-config!
  ([config]
   (validate-config! be.schema/?build-config config))
  ([?schema config]
   (when-let [e (m/explain ?schema config)]
     (let [m (me/humanize e)]
       (throw (ex-info (str "Invalid config: " m) m))))))

(defn- gen-config
  [arg]
  (or (:config arg)
      (let [render-data (be.var/variable-map)
            config (cond-> arg
                     (and (:version arg)
                          (str/includes? (:version arg) "{{"))
                     (update :version #(pg/render-string % render-data)))
            config (cond-> config
                     (contains? config :scm)
                     (assoc-in [:scm :tag] (:version config)))
            defaults (gen-default-configs)]
        (-> defaults
            (merge config)
            (update :deploy-repository #(merge (:deploy-repository defaults) %))))))

(defn- get-basis
  [arg]
  (or (:basis arg)
      (-> (select-keys arg [:aliases])
          (b/create-basis))))

(defn- get-src-dirs
  [config basis]
  (or (:src-dirs config)
      (:paths basis)))

(defn- set-gha-output
  [config k v]
  (when (:github-actions? config)
    (println (str "::set-output name=" k "::" v))))

(defn pom
  [arg]
  (let [config (gen-config arg)
        _ (validate-config! config)
        basis (get-basis arg)
        pom-path (b/pom-path config)]
    (-> config
        (select-keys [:lib :version :class-dir :scm])
        (assoc :basis basis
               :src-dirs (get-src-dirs config basis))
        (b/write-pom))
    (set-gha-output config "pom" pom-path)
    pom-path))

(defn jar
  [arg]
  (let [{:as config :keys [lib class-dir jar-file]} (gen-config arg)
        _ (validate-config! config)
        basis (get-basis arg)
        arg (assoc arg :config config :basis basis)
        render-data {:lib (name lib)}
        jar-file (pg/render-string jar-file render-data)]
    (pom arg)
    (b/copy-dir {:src-dirs (get-src-dirs config basis)
                 :target-dir class-dir})
    (b/jar {:class-dir class-dir
            :jar-file jar-file})
    (set-gha-output config "jar" jar-file)
    jar-file))

(defn uberjar
  [arg]
  (let [{:as config :keys [lib class-dir uber-file main]} (gen-config arg)
        ?schema (mu/merge be.schema/?build-config be.schema/?uber-build-config)
        _ (validate-config! ?schema config)
        basis (get-basis arg)
        src-dirs (get-src-dirs config basis)
        arg (assoc arg :config config :basis basis)
        render-data {:lib (name lib)}
        uber-file (pg/render-string uber-file render-data)]
    (pom arg)
    (b/copy-dir {:src-dirs src-dirs
                 :target-dir class-dir})
    (b/compile-clj {:basis basis
                    :src-dirs src-dirs
                    :class-dir class-dir})
    (b/uber {:class-dir class-dir
             :uber-file uber-file
             :basis basis
             :main main})
    (set-gha-output config "uberjar" uber-file)
    uber-file))

(defn install
  [arg]
  (let [{:as config :keys [lib version class-dir]} (gen-config arg)
        _ (validate-config! config)
        arg (assoc arg :config config)
        jar-file (jar arg)]
    (deploy/deploy {:artifact jar-file
                    :installer :local
                    :pom-file (b/pom-path {:lib lib :class-dir class-dir})})
    (set-gha-output config "version" version)
    version))

(defn deploy
  [arg]
  (let [{:as config :keys [lib version class-dir deploy-repository]} (gen-config arg)
        ?schema (mu/merge be.schema/?build-config be.schema/?deploy-repositories-build-config)
        _ (validate-config! ?schema config)
        repo (cond-> deploy-repository
               (some? (:gpg/credential-file deploy-repository))
               (merge (be.gpg/get-credential-by-repository deploy-repository)))
        arg (assoc arg :config config)
        jar-file (jar arg)]

    (when (:allow-insecure-http-repository? repo)
      (aether/register-wagon-factory! "http" #(HttpWagon.)))

    (deploy/deploy {:artifact jar-file
                    :installer :remote
                    :pom-file (b/pom-path {:lib lib :class-dir class-dir})
                    :repository {"release" (dissoc repo
                                                   :allow-insecure-http-repository?)}})

    (set-gha-output config "version" version)
    version))

(defn- update-line
  [f s]
  (let [tail-blanks (if (re-seq #"^\s*$" s)
                      ""
                      (first (re-seq #"\s*$" s)))
        result (->> (str/split-lines s)
                    (mapcat #(f [%]))
                    (str/join "\n"))]
    (str result tail-blanks)))

(defn update-documents
  [arg]
  (let [{:as config :keys [version documents]} (gen-config arg)
        ?schema (mu/merge be.schema/?build-config be.schema/?documents-build-config)
        _ (validate-config! ?schema config)
        render-data (assoc (be.var/variable-map)
                           :version version)]
    (doseq [{:keys [file match action text]} documents
            :let [regexp (re-pattern match)
                  text (pg/render-string text render-data)]]
      (->> (slurp file)
           (update-line (fn [[line]]
                          (if (re-find regexp line)
                            (case action
                              :append-before [text line]
                              :append-after [line text]
                              [text])
                            [line])))
           (spit file)))
    (set-gha-output config "version" version)
    version))

(defn lint
  [arg]
  (let [config (gen-config arg)
        ?schema (cond-> be.schema/?build-config
                  (contains? config :documents)
                  (mu/merge be.schema/?documents-build-config))]
    (if-let [e (m/explain ?schema config)]
      (do (println (me/humanize e))
          false)
      (do (println "OK")
          true))))
