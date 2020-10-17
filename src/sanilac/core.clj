(ns sanilac.core
  (:require [clj-http.client :as client])
  (:use [slingshot.slingshot :only [throw+ try+]])
  (:gen-class))

(defn get-request [url]
  (try+
    (client/get url)
    (catch [:status 403] {:keys [request-time headers body]}
      (println (str "403" request-time " " headers)))
    (catch [:status 404] {:keys [request-time]}
      (println (str "404" request-time " " url)))
    (catch Object _
      (println "Unexpected error"))))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (get-request "https://www.example.com")
  (println "Hello, World!"))
