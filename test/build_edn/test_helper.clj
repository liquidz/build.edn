(ns build-edn.test-helper
  (:require
   [malli.instrument :as mi]))

(defn malli-instrument-fixture
  [f]
  (mi/instrument!)
  (f))
