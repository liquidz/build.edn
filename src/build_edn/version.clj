(ns build-edn.version
  (:require
   [clojure.string :as str]
   [malli.core :as m]
   [malli.experimental :as mx]))

(def ^:private ?parsed-version
  [:map {:closed true}
   [:version/major 'string?]
   [:version/minor 'string?]
   [:version/patch 'string?]
   [:version/snapshot? 'boolean?]])

(def ^:private ?version-type
  [:enum
   :major
   :minor
   :patch])

(mx/defn parse-semantic-version
  :- [:maybe ?parsed-version]
  [s]
  (when (string? s)
    (let [snapshot? (str/ends-with? s "-SNAPSHOT")
          [_ major minor patch] (->> (str/replace s #"-SNAPSHOT$" "")
                                     (re-seq #"^([^.]+)\.([^.]+)\.([^.]+)$")
                                     (first))
          parsed {:version/major major
                  :version/minor minor
                  :version/patch patch
                  :version/snapshot? snapshot?}]
      (when (m/validate ?parsed-version parsed)
        parsed))))

(mx/defn to-semantic-version
  :- 'string?
  [{:version/keys [major minor patch snapshot?]} :- ?parsed-version]
  (cond-> (format "%s.%s.%s" major minor patch)
    snapshot? (str "-SNAPSHOT")))

(mx/defn bump-version
  :- [:maybe ?parsed-version]
  ([parsed-version]
   (bump-version parsed-version :patch))
  ([parsed-version :- ?parsed-version
    version-type :- ?version-type]
   (let [target-key (keyword "version" (name version-type))]
     (when-let [new-version (some-> (get parsed-version target-key)
                                    parse-long inc str)]
       (assoc parsed-version target-key new-version)))))

(mx/defn add-snapshot
  :- ?parsed-version
  [parsed-version :- ?parsed-version]
  (assoc parsed-version :version/snapshot? true))

(mx/defn remove-snapshot
  :- ?parsed-version
  [parsed-version :- ?parsed-version]
  (assoc parsed-version :version/snapshot? false))
