(ns sanilac.web
  (:require [compojure.core :refer [defroutes GET]]
            [ring.adapter.jetty :as ring]))

(defroutes routes
           (GET "/" [] "<h2>Hello World</h2>"))

(defn run []
  (ring/run-jetty #'routes {:port 8080 :join? false}))
