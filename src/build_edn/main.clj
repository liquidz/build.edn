(ns build-edn.main
  (:require
   [build-edn.core :as core]
   [clojure.edn :as edn]))

(defn- load-config
  []
  (-> (slurp "build.edn")
      (edn/read-string)))

(defn pom
  [_]
  (core/pom (load-config)))

(defn jar
  [_]
  (core/jar (load-config)))

(defn uberjar
  [_]
  (core/uberjar (load-config)))

(defn install
  [_]
  (core/install (load-config)))

(defn tag-changelog
  [_]
  (core/tag-changelog (load-config)))
