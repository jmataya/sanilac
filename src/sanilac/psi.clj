(ns sanilac.psi
  (:require [clojure.data.json :as json]
            [clj-http.client :as client])
  (:use [slingshot.slingshot :only [throw+ try+]]))

(def score-ranges
  {:cls {:fast {:min 0 :max 10}
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

(defn parse-category
  [category]
  (cond
    (= category "SLOW") :slow
    (= category "AVERAGE") :average
    (= category "FAST") :fast))

(defn in-range? [range distribution]
  (and
    (= (distribution "min") (range :min))
    (= (distribution "max") (range :max))))

(defn parse-distribution
  [metric distribution]
  (let [slow-range (get-in score-ranges [metric :slow])
        average-range (get-in score-ranges [metric :average])
        fast-range (get-in score-ranges [metric :fast])]
    (cond
      (in-range? fast-range distribution) :fast
      (in-range? average-range distribution) :average
      (in-range? slow-range distribution) :slow)))

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
        category (parse-category (get cls-metrics "category"))]
    (assoc distributions :category category :percentile percentile)))

(defn parse-error
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

(defn get-request
  "Wrapper around the Page Speed Insights request to return results or an error."
  [url]
  (try+
    (let [resp (client/get url)
          resp-body (get resp :body)]
      (parse-field-cls resp-body))
    (catch [:status 400] {:keys [body]}
      (assoc-in (parse-error body) [:error :type] "BAD_REQUEST"))
    (catch [:status 429] {:keys [body]}
      (assoc-in (parse-error body) [:error :type] "RATE_LIMIT"))))

(defn query
  "Makes an API request to the Google Page Speed Insights API for a requested URL."
  [url api-key]
  (let [request-url (str "https://www.googleapis.com/pagespeedonline/v5/runPagespeed?url=" url "&key=" api-key)]
    (get-request request-url)))
