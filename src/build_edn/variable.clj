(ns build-edn.variable
  (:require
   [clojure.tools.build.api :as b])
  (:import
   java.time.ZonedDateTime
   java.time.format.DateTimeFormatter))

(defn- current-yyyy
  []
  (.format (ZonedDateTime/now) (DateTimeFormatter/ofPattern "yyyy")))

(defn- current-mm
  []
  (.format (ZonedDateTime/now) (DateTimeFormatter/ofPattern "MM")))

(defn- current-m
  []
  (.format (ZonedDateTime/now) (DateTimeFormatter/ofPattern "M")))

(defn- current-dd
  []
  (.format (ZonedDateTime/now) (DateTimeFormatter/ofPattern "dd")))

(defn- current-d
  []
  (.format (ZonedDateTime/now) (DateTimeFormatter/ofPattern "d")))

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

(defn variable-map
  []
  {:git/commit-count (git-commit-count)
   :git/head-long-sha (git-head-revision false)
   :git/head-short-sha (git-head-revision true)
   :now/yyyy (current-yyyy)
   :now/mm (current-mm)
   :now/m (current-m)
   :now/dd (current-dd)
   :now/d (current-d)})
