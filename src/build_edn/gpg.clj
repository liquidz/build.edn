(ns build-edn.gpg
  (:require
   [clojure.java.shell :as sh]))

(defn- gpg-decrypt
  [filepath]
  (let [{:as ret :keys [out err]} (sh/sh "gpg" "--quiet" "--batch" "--decrypt" filepath)]
    (when (seq err)
      (throw (ex-info err ret)))
    out))

(defn get-credential-by-repository
  [{:keys [url]
    :gpg/keys [credential-file]}]
  (let [out (gpg-decrypt credential-file)
        m (some->> out
                   (read-string)
                   (some (fn [[re v]] (and (re-seq re url) v))))]
    (assert (map? m) (str "credential-file must contain a setting for " url))
    (assert (contains? m :username) (str "a setting for " url " must contain :username"))
    (assert (contains? m :password) (str "a setting for " url " must contain :password"))
    m))
