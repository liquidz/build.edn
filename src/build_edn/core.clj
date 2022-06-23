(ns build-edn.core
  (:require
   [build-edn.pom :as be.pom]
   [build-edn.repository :as be.repo]
   [build-edn.schema :as be.schema]
   [build-edn.variable :as be.var]
   [build-edn.version :as be.ver]
   [clojure.string :as str]
   [clojure.tools.build.api :as b]
   [deps-deploy.deps-deploy :as deploy]
   [malli.core :as m]
   [malli.error :as me]
   [malli.util :as mu]
   [pogonos.core :as pg]))

(def ^:private default-configs
  {:class-dir "target/classes"
   :jar-file "target/{{lib}}.jar"
   :uber-file "target/{{lib}}-standalone.jar"
   :deploy-repository {:id "clojars"}
   :github-actions? false})

(defn- validate-config!
  ([config]
   (validate-config! be.schema/?build-config config))
  ([?schema config]
   (when-let [e (m/explain ?schema config)]
     (let [m (me/humanize e)]
       (throw (ex-info (str "Invalid config: " m) m))))))

(defn- generate-render-data
  ([]
   (generate-render-data {}))
  ([{:keys [lib version]}]
   (merge (be.var/variable-map)
          (and lib {:lib (name lib)})
          (and version {:version version})
          (and version (be.ver/parse-semantic-version version)))))

(defn- gen-config
  [arg]
  (or (:config arg)
      (let [render-data (generate-render-data)
            config (cond-> arg
                     (and (:version arg)
                          (str/includes? (:version arg) "{{"))
                     (update :version #(pg/render-string % render-data)))
            config (if-let [scm (and (not (contains? config :scm))
                                     (be.pom/generate-scm-from-git-dir))]
                     (assoc config :scm scm)
                     config)
            config (cond-> config
                     (contains? config :scm)
                     (assoc-in [:scm :tag] (:version config)))]
        (merge default-configs config))))

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
  (let [{:as config :keys [class-dir jar-file]} (gen-config arg)
        _ (validate-config! config)
        basis (get-basis arg)
        arg (assoc arg :config config :basis basis)
        jar-file (->> (generate-render-data config)
                      (pg/render-string jar-file))]
    (pom arg)
    (b/copy-dir {:src-dirs (get-src-dirs config basis)
                 :target-dir class-dir})
    (b/jar {:class-dir class-dir
            :jar-file jar-file})
    (set-gha-output config "jar" jar-file)
    jar-file))

(defn uberjar
  [arg]
  (let [{:as config :keys [class-dir uber-file main]} (gen-config arg)
        ?schema (mu/merge be.schema/?build-config be.schema/?uber-build-config)
        _ (validate-config! ?schema config)
        basis (get-basis arg)
        src-dirs (get-src-dirs config basis)
        arg (assoc arg :config config :basis basis)
        uber-file (->> (generate-render-data config)
                       (pg/render-string uber-file))]
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
        ?schema (mu/merge be.schema/?build-config be.schema/?deploy-repository-build-config)
        _ (validate-config! ?schema config)
        {:keys [id username password url]} deploy-repository
        arg (assoc arg :config config)
        jar-file (jar arg)
        repo (some-> id
                     (be.repo/repository-by-id)
                     (cond->
                      username (assoc-in [id :username] username)
                      password (assoc-in [id :password] password)
                      url (assoc-in [id :url] url)))]
    (deploy/deploy (cond-> {:artifact jar-file
                            :installer :remote
                            :pom-file (b/pom-path {:lib lib :class-dir class-dir})}
                     repo (assoc :repository repo)))
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
        render-data (generate-render-data config)]
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
