(ns build-edn.util.string)

(defn add-indent
  [base target]
  (let [base-indent (first (re-seq #"^\s+" base))
        [_ target-indent target-body] (or (first (re-seq #"(^\s+)(.+)$" target))
                                          [nil "" target])]
    (str target-indent base-indent target-body)))
