(ns sanilac.core
  (:require [environ.core :refer [env]]
            [sanilac.psi :as psi]
            [sanilac.web :as web])
  (:gen-class))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (let [psi-api-key (env :psi-api-key)]
    (println (psi/query "https://www.bankrate.com" psi-api-key))
    (web/run)))
