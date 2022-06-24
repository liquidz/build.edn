(ns build-edn.pom
  (:require
   [clojure.data.xml :as xml]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.zip :as zip]))

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
         domain (when url
                  (condp #(str/includes? %2 %1) url
                    "github.com" "github.com"
                    "gitlab.com" "gitlab.com"
                    nil))
         user-repo (when domain (extract-user-and-repository domain url))]
     (when user-repo
       (scm-map domain user-repo)))))

(defn- directly-under-project?
  [loc]
  (if-let [parent-tag (:tag (zip/node (zip/up loc)))]
    (= "project" (name parent-tag))
    false))

(defn- contains-tag-directly-under-project?
  [loc target-tag-name]
  (loop [loc loc]
    (if (and loc (zip/end? loc))
      false
      (let [tag (:tag (zip/node loc))]
        (if (and tag
                 (= target-tag-name (name tag))
                 (directly-under-project? loc))
          true
          (recur (zip/next loc)))))))

(defn- add-new-tag-before-name-tag
  [^String pom-content ^String new-tag-name ^String new-tag-content]
  (let [loc (-> (.getBytes pom-content)
                (io/input-stream)
                (xml/parse :skip-whitespace true)
                (zip/xml-zip))]
    (if (contains-tag-directly-under-project? loc new-tag-name)
      pom-content
      (loop [loc loc]
        (if (and loc (zip/end? loc))
          (-> loc
              (zip/root)
              (xml/indent-str))
          (let [{:as node :keys [tag]} (zip/node loc)]
            (if (and tag
                     (= "name" (name tag))
                     (directly-under-project? loc))
              (let [new-node (-> node
                                 (update :tag #(keyword (namespace %) new-tag-name))
                                 (assoc :content [new-tag-content]))]
                (recur (zip/next (zip/insert-left loc new-node))))
              (recur (zip/next loc)))))))))

(defn add-description
  [^String pom-content ^String description]
  (add-new-tag-before-name-tag pom-content "description" description))
