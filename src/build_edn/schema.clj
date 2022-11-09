(ns build-edn.schema)

(def ?build-config
  [:map
   [:config-file {:optional true} string?] ; The file path containing build configuration
   [:lib qualified-symbol?]
   [:version string?]
   [:description {:optional true} string?]
   [:source-dirs {:optional true} [:or
                                   [:sequential string?]
                                   [:set string?]]]
   [:class-dir {:optional true} string?]
   [:jar-file {:optional true} string?]
   [:github-actions? {:optional true} boolean?]])

(def ?uber-build-config
  [:map
   [:uber-file string?]
   [:main symbol?]
   [:skip-compiling-dirs {:optional true} [:or
                                           [:sequential string?]
                                           [:set string?]]]])

(def ?document
  [:or
   [:map {:closed true}
    [:file string?]
    [:match string?]
    [:action [:enum :append-before :replace :append-after]]
    [:keep-indent? {:optional true} boolean?]
    [:text string?]]
   [:map {:closed true}
    [:file string?]
    [:action [:enum :create]]
    [:text string?]]])

(def ?documents-build-config
  [:map [:documents [:sequential ?document]]])

(def ?deploy-repository
  [:map
   [:id string?]
   [:username {:optional true} string?]
   [:password {:optional true} string?]
   [:url {:optional true} string?]])

(def ?deploy-repository-build-config
  [:map
   [:deploy-repository ?deploy-repository]])

(def ?scm
  [:map
   [:connection string?]
   [:developerConnection string?]
   [:url string?]])

(def ?pom
  [:map
   [:scm {:optional true} ?scm]
   [:no-clojure-itself? {:optional true} boolean?]])

(def ?pom-build-config
  [:map
   [:pom ?pom]])
