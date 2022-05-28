(ns build-edn.main
  (:require
   [build-edn.core :as core]
   [aero.core :as aero]
   [clojure.edn :as edn]))

(defn- load-config
  []
  (aero/read-config "build.edn"))

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
  (-> (load-config)
      (merge m)
      (core/install)))

(defn deploy
  [m]
  (-> (load-config)
      (merge m)
      (core/deploy)))

(defn update-documents
  [m]
  (-> (load-config)
      (merge m)
      (core/update-documents)))

(defn lint
  [m]
  (-> (load-config)
      (merge m)
      (core/lint)))
