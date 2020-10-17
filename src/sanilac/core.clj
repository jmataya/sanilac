(ns sanilac.core
  (:require [clj-http.client :as client]
            [environ.core :refer [env]])
  (:use [slingshot.slingshot :only [throw+ try+]])
  (:gen-class))

(defn query-psi
  "Makes an API request to the Google Page Speed Insights API for a requested URL."
  [url api-key]
  (let [request-url (str "https://www.googleapis.com/pagespeedonline/v5/runPagespeed?url=" url "&key=" api-key)]
    (client/get request-url)))

;(defn get-request [url]
;  (try+
;    (client/get url)
;    (catch [:status 403] {:keys [request-time headers body]}
;      (println (str "403" request-time " " headers)))
;    (catch [:status 404] {:keys [request-time]}
;      (println (str "404" request-time " " url)))
;    (catch Object _
;      (println "Unexpected error"))))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (let [psi-api-key (env :psi-api-key)]
    (query-psi "https://www.bankrate.com" psi-api-key)))
