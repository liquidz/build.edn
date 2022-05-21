(ns build-edn.core
  (:require
   [clojure.string :as str]
   [clojure.tools.build.api :as b]
   [deps-deploy.deps-deploy :as deploy]
   [malli.core :as m]
   [malli.error :as me]
   [malli.util :as mu]
   [pogonos.core :as pg])
  (:import
   java.time.ZonedDateTime
   java.time.format.DateTimeFormatter))

(def ^:private ?scm
  [:map
   [:connection string?]
   [:developerConnection string?]
   [:url string?]])

(def ^:private ?build-config
  [:map
   [:lib qualified-symbol?]
   [:version string?]
   [:source-dir {:optional true} string?]
   [:class-dir {:optional true} string?]
   [:jar-file {:optional true} string?]
   [:scm {:optional true} ?scm]
   [:github-actions? {:optional true} boolean?]])

(def ^:private ?uber-build-config
  (mu/merge ?build-config
            [:map
             [:uber-file string?]
             [:main symbol?]]))

(def ^:private ?document
  [:map
   [:file string?]
   [:match string?]
   [:action [:enum :append-before :replace :append-after]]
   [:text string?]])

(def ^:private ?documents-build-config
  (mu/merge ?build-config
            [:map
             [:documents [:sequential ?document]]]))

(defn- validate-config!
  ([config]
   (validate-config! ?build-config config))
  ([?schema config]
   (when-let [e (m/explain ?schema config)]
     (let [m (me/humanize e)]
       (throw (ex-info (str "Invalid config: " m) m))))))

(defn- get-defaults
  [config]
  (let [lib-name (name (:lib config))]
    {:class-dir "target/classes"
     :jar-file (format "target/%s.jar" lib-name)
     :uber-file (format "target/%s-standalone.jar" lib-name)
     :github-actions? false}))

(defn- current-yyyy-mm-dd
  []
  (.format (ZonedDateTime/now) DateTimeFormatter/ISO_LOCAL_DATE))

(defn- git-commit-count
  []
  (or (b/git-count-revs nil) 0))

(defn- git-head-revision
  [short?]
  (b/git-process
   {:git-command "git"
    :git-args (cond-> ["rev-parse"]
                short? (conj "--short")
                :always (conj "HEAD"))}))

(defn- gen-config
  [arg]
  (or (:config arg)
      (let [render-data {:commit-count (git-commit-count)}
            config (cond-> arg
                     (str/includes? (:version arg) "{{")
                     (update :version #(pg/render-string % render-data)))
            config (cond-> config
                     (contains? config :scm)
                     (assoc-in [:scm :tag] (:version config)))]
        (merge (get-defaults config) config))))

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
        basis (get-basis arg)]
    (validate-config! config)
    (-> config
        (select-keys [:lib :version :class-dir :scm])
        (assoc :basis basis
               :src-dirs (get-src-dirs config basis))
        (b/write-pom))
    (set-gha-output config "pom" (b/pom-path config))))

(defn jar
  [arg]
  (let [{:as config :keys [class-dir jar-file]} (gen-config arg)
        basis (get-basis arg)
        arg (assoc arg :config config :basis basis)]
    (validate-config! config)
    (pom arg)
    (b/copy-dir {:src-dirs (get-src-dirs config basis)
                 :target-dir class-dir})
    (b/jar {:class-dir class-dir
            :jar-file jar-file})
    (set-gha-output config "jar" jar-file)))

(defn uberjar
  [arg]
  (let [{:as config :keys [class-dir uber-file main]} (gen-config arg)
        basis (get-basis arg)
        src-dirs (get-src-dirs config basis)
        arg (assoc arg :config config :basis basis)]
    (validate-config! ?uber-build-config config)
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
    (set-gha-output config "uberjar" uber-file)))

(defn install
  [arg]
  (let [{:as config :keys [lib version class-dir jar-file]} (gen-config arg)
        arg (assoc arg :config config)]
    (validate-config! config)
    (jar arg)
    (deploy/deploy {:artifact jar-file
                    :installer :local
                    :pom-file (b/pom-path {:lib lib :class-dir class-dir})})
    (set-gha-output config "version" version)))

(defn deploy
  [arg]
  (assert (and (System/getenv "CLOJARS_USERNAME")
               (System/getenv "CLOJARS_PASSWORD")))
  (let [{:as config :keys [lib version class-dir jar-file]} (gen-config arg)
        arg (assoc arg :config config)]
    (validate-config! config)
    (jar arg)
    (deploy/deploy {:artifact jar-file
                    :installer :remote
                    :pom-file (b/pom-path {:lib lib :class-dir class-dir})})
    (set-gha-output config "version" version)))

(defn update-documents
  [arg]
  (let [{:as config :keys [version documents]} (gen-config arg)
        _ (validate-config! ?documents-build-config config)
        render-data {:version version
                     :git-head-long-sha (git-head-revision false)
                     :git-head-short-sha (git-head-revision true)
                     :yyyy-mm-dd (current-yyyy-mm-dd)}]
    (doseq [{:keys [file match action text]} documents
            :let [regexp (re-pattern match)
                  text (pg/render-string text render-data)]]
      (->> (slurp file)
           (str/split-lines)
           (mapcat #(if (re-find regexp %)
                      (case action
                        :append-before [text %]
                        :append-after [% text]
                        [text])
                      [%]))
           (str/join "\n")
           (spit file)))
    (set-gha-output config "version" version)))
