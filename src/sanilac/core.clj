(ns sanilac.core
  (:require [clojure.data.json :as json]
            [clj-http.client :as client]
            [environ.core :refer [env]])
  (:use [slingshot.slingshot :only [throw+ try+]])
  (:gen-class))

(def psi-ranges {:cls {:fast {:min 0 :max 10}
                       :average {:min 10 :max 25}
                       :slow {:min 25}}
                 :lcp {:fast {:min 0 :max 2500}
                       :average {:min 2500 :max 4000}
                       :slow {:min 4000}}
                 :fcp {:fast {:min 0 :max 1000}
                       :average {:min 1000 :max 3000}
                       :slow {:min 3000}}
                 :fid {:fast {:min 0 :max 100}
                       :average {:min 100 :max 300}
                       :slow {:min 300}}})

(defn parse-psi-error
  "Extract error details from a failed Page Speed Insights API request."
  [request-body]
  (let [error-json (json/read-str request-body)
        status-code (get-in error-json ["error" "code"])
        status (get-in error-json ["error" "status"])
        message (reduce
                  (fn [messages-str error]
                    (let [error-message (get error "message")]
                      (if (empty? messages-str)
                        error-message
                        (str messages-str ", " error-message))))
                  ""
                  (get-in error-json ["error" "errors"]))]
    {:error {:status-code status-code :status status :message message}}))

(defn psi-get-request
  "Wrapper around the Page Speed Insights request to return results or an error."
  [url]
  (try+
    (let [resp (client/get url)
          resp-body (get resp :body)]
      resp-body)
    (catch [:status 400] {:keys [body]}
      (assoc-in (parse-psi-error body) [:error :type] "BAD_REQUEST"))
    (catch [:status 429] {:keys [body]}
      (assoc-in (parse-psi-error body) [:error :type] "RATE_LIMIT"))))

(defn query-psi
  "Makes an API request to the Google Page Speed Insights API for a requested URL."
  [url api-key]
  (let [request-url (str "https://www.googleapis.com/pagespeedonline/v5/runPagespeed?url=" url "&key=" api-key)]
    (psi-get-request request-url)))

(defn parse-psi-category
  [category]
  (cond
    (= category "SLOW") :slow
    (= category "AVERAGE") :average
    (= category "FAST") :fast))

(defn parse-distribution
  [metric distribution]
  (let [slow-range (get-in psi-ranges [metric :slow])
        average-range (get-in psi-ranges [metric :average])
        fast-range (get-in psi-ranges [metric :fast])
        dist-min (get distribution "min")
        dist-max (get distribution "max")]
    (cond
           (and (= dist-min (fast-range :min)) (= dist-max (fast-range :max))) :fast
           (and (= dist-min (average-range :min)) (= dist-max (average-range :max))) :average
           (and (= dist-min (slow-range :min)) (= dist-max (slow-range :max))) :slow)))

(defn parse-cls-distributions
  "Takes a distribution object adds it to a structured map."
  [distributions]
  (reduce (fn [m distribution]
            (let [key (parse-distribution :cls distribution)
                  proportion (get distribution "proportion")]
              (assoc m key proportion)))
          {}
          distributions))

(defn parse-field-cls
  "Extracts the distributions for CLS from field data."
  [psi-result]
  (let [cls-metrics (get-in psi-result ["loadingExperience" "metrics" "CUMULATIVE_LAYOUT_SHIFT_SCORE"])
        percentile (get cls-metrics "percentile")
        distributions (parse-cls-distributions (get cls-metrics "distributions"))
        category (parse-psi-category (get cls-metrics "category"))]
    (assoc distributions :category category :percentile percentile)))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (let [psi-api-key (env :psi-api-key)]
    (println (query-psi "https://www.bankrate.com" psi-api-key))))
