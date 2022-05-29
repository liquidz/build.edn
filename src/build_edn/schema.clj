(ns build-edn.schema)

(def ?scm
  [:map
   [:connection string?]
   [:developerConnection string?]
   [:url string?]])

(def ?build-config
  [:map
   [:lib qualified-symbol?]
   [:version string?]
   [:source-dir {:optional true} string?]
   [:class-dir {:optional true} string?]
   [:jar-file {:optional true} string?]
   [:scm {:optional true} ?scm]
   [:github-actions? {:optional true} boolean?]])

(def ?uber-build-config
  [:map
   [:uber-file string?]
   [:main symbol?]])

(def ?document
  [:map
   [:file string?]
   [:match string?]
   [:action [:enum :append-before :replace :append-after]]
   [:text string?]])

(def ?documents-build-config
  [:map
   [:documents [:sequential ?document]]])

(def ?deploy-repository
  [:map
   [:id string?]
   [:username {:optional true} string?]
   [:password {:optional true} string?]
   [:url {:optional true} string?]])

(def ?deploy-repository-build-config
  [:map
   [:deploy-repository ?deploy-repository]])
