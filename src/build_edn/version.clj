(ns build-edn.version
  (:require
   [clojure.string :as str]))

(defn parse-semantic-version
  [s]
  (let [coll (when (string? s)
               (str/split s #"\."))]
    (when (= 3 (count coll))
      {:version/major (nth coll 0)
       :version/minor (nth coll 1)
       :version/patch (nth coll 2)})))
