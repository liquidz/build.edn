(ns build-edn.pom
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]))

(defn- remote-origin-url-regexps
  [domain]
  (let [domain (str/replace domain "." "\\.")]
    [(re-pattern (format "(?:\\w+@)?%s:([^/]+)/([^/]+)\\.git" domain))
     (re-pattern (format "\\w+://(?:\\w+@)?%s/([^/]+)/([^/]+?)(?:\\.git)?" domain))]))

(defn- git-remote-origin-url
  [git-config-path]
  (try
    (let [url (some->> (slurp git-config-path)
                       (str/split-lines)
                       (drop-while #(not= "[remote \"origin\"]" (str/trim %)))
                       (drop-while #(not (re-find #"url\s*=\s" %)))
                       (first))]
      (some-> url
              (str/split #"\s*=\s*" 2)
              (second)))
    (catch Exception _ nil)))

(defn- extract-user-and-repository
  [domain remote-origin-url]
  (some->> domain
           (remote-origin-url-regexps)
           (some #(re-matches % remote-origin-url))
           (rest)))

(defn- scm-map
  [domain [user repo]]
  {:connection (format "scm:git:git://%s/%s/%s.git" domain user repo)
   :developerConnection (format "scm:git:ssh://git@%s/%s/%s.git" domain user repo)
   :url (format "https://%s/%s/%s" domain user repo)})

(defn generate-scm-from-git-dir
  ([]
   (generate-scm-from-git-dir ".git"))
  ([git-dir]
   (let [git-config-path (io/file git-dir "config")
         url (git-remote-origin-url git-config-path)
         domain (condp #(str/includes? %2 %1) url
                  "github.com" "github.com"
                  "gitlab.com" "gitlab.com"
                  nil)
         user-repo (when domain (extract-user-and-repository domain url))]
     (when user-repo
       (scm-map domain user-repo)))))
