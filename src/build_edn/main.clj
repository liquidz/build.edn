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

(defn help
  "Print this help"
  [_]
  (let [metas (->> (ns-publics 'build-edn.main)
                   (vals)
                   (map meta)
                   (sort-by #(:name %)))]
    (doseq [m metas]
      (println (str (:name m) " - " (:doc m))))))
