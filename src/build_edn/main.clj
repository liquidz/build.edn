(ns build-edn.main
  (:require
   [build-edn.core :as core]
   [clojure.edn :as edn]))

(defn- load-config
  []
  (-> (slurp "build.edn")
      (edn/read-string)))

(defn pom
  [m]
  (-> (load-config)
      (merge m)
      (core/pom)))

(defn jar
  [m]
  (-> (load-config)
      (merge m)
      (core/jar)))

(defn uberjar
  [m]
  (-> (load-config)
      (merge m)
      (core/uberjar)))

(defn install
  [m]
  (println "FIXME" (pr-str m))
  (-> (load-config)
      (merge m)
      (core/install)))

(defn deploy
  [m]
  (-> (load-config)
      (merge m)
      (core/deploy)))

(defn tag-changelog
  [m]
  (-> (load-config)
      (merge m)
      (core/tag-changelog)))
