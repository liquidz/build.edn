(ns build-edn.main
  (:require
   [aero.core :as aero]
   [build-edn.core :as core]))

(defn- load-config
  ([]
   (load-config "build.edn"))
  ([path]
   (-> path
       (aero/read-config)
       (assoc :config-file path))))

(defn pom
  "Generate pom.xml"
  [m]
  (-> (load-config)
      (merge m)
      (core/pom)))

(defn jar
  "Generate JAR file"
  [m]
  (-> (load-config)
      (merge m)
      (core/jar)))

(defn java-compile
  "Compile Java sources"
  [m]
  (-> (load-config)
      (merge m)
      (core/java-compile)))

(defn uberjar
  "Generate standalone JAR file"
  [m]
  (-> (load-config)
      (merge m)
      (core/uberjar)))

(defn install
  "Install this library to your local Maven repository(~/.m2)"
  [m]
  (-> (load-config)
      (merge m)
      (core/install)))

(defn deploy
  "Deploy this library to a remote Maven repository"
  [m]
  (-> (load-config)
      (merge m)
      (core/deploy)))

(defn update-documents
  "Update document files for a new release"
  [m]
  (-> (load-config)
      (merge m)
      (core/update-documents)))

(defn lint
  "Lint your build.edn file"
  [m]
  (-> (load-config)
      (merge m)
      (core/lint)))

(defn bump-patch-version
  "Bump patch version and update configuration file"
  [m]
  (-> (load-config)
      (merge m)
      (core/bump-version :patch)))

(defn bump-minor-version
  "Bump minor version and update configuration file"
  [m]
  (-> (load-config)
      (merge m)
      (core/bump-version :minor)))

(defn bump-major-version
  "Bump major version and update configuration file"
  [m]
  (-> (load-config)
      (merge m)
      (core/bump-version :major)))

(defn add-snapshot
  "Add '-SNAPSHOT' to version number and update configuration file"
  [m]
  (-> (load-config)
      (merge m)
      (core/add-snapshot)))

(defn remove-snapshot
  "Remove '-SNAPSHOT' from version number and update configuration file"
  [m]
  (-> (load-config)
      (merge m)
      (core/remove-snapshot)))

(defn help
  "Print this help"
  [_]
  (let [metas (->> (ns-publics 'build-edn.main)
                   (vals)
                   (map meta)
                   (sort-by #(:name %)))
        max-len (apply max (map (comp count str :name) metas))
        format-str (format "%%-%ds - %%s" max-len)]
    (doseq [m metas]
      (println (format format-str  (:name m) (:doc m))))))
