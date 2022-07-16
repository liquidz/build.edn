(ns build-edn.schema
  (:require
   [malli.experimental.lite :as l]))

(defn- enum
  [& xs]
  (-> (cons :enum xs)
      (vec)
      (l/schema)))

(defn- sequential
  [x]
  (l/schema [:sequential (l/schema x)]))

(def ?scm
  (l/schema
   {:connection string?
    :developerConnection string?
    :url string?}))

(def ?build-config
  (l/schema
   {:lib qualified-symbol?
    :version string?
    :description (l/optional string?)
    :source-dir (l/optional string?)
    :class-dir (l/optional string?)
    :jar-file (l/optional string?)
    :scm (l/optional ?scm)
    :github-actions? (l/optional boolean?)}))

(def ?uber-build-config
  (l/schema
   {:uber-file string?
    :main symbol?
    :skip-compiling-dirs (l/optional (l/or (sequential string?)
                                           (l/set string?)))}))

(def ?document
  (l/or
   (l/schema
    {:file string?
     :match string?
     :action (enum :append-before :replace :append-after)
     :text string?})
   (l/schema
    {:file string?
     :action (enum :create)
     :text string?})))

(def ?documents-build-config
  (l/schema
   {:documents (sequential ?document)}))

(def ?deploy-repository
  (l/schema
   {:id string?
    :username (l/optional string?)
    :password (l/optional string?)
    :url (l/optional string?)}))

(def ?deploy-repository-build-config
  (l/schema
   {:deploy-repository ?deploy-repository}))
