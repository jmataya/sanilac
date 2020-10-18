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

(def field-categories
  {:cls "CUMULATIVE_LAYOUT_SHIFT_SCORE"
   :fcp "FIRST_CONTENTFUL_PAINT_MS"
   :fid "FIRST_INPUT_DELAY_MS"
   :lcp "LARGEST_CONTENTFUL_PAINT_MS"})

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

(defn parse-distributions
  "Takes a distribution object adds it to a structured map."
  [distributions metric]
  (reduce (fn [m distribution]
            (let [key (parse-distribution metric distribution)
                  proportion (distribution "proportion")]
              (assoc m key proportion)))
          {}
          distributions))

(defn field-metrics
  "Extracts a given field metrics object from psi-result based on the key passed in."
  [psi-result key]
  (let [key-str (field-categories key)]
    (get-in (json/read-str psi-result) ["loadingExperience" "metrics" key-str])))

(defn parse-field
  "Extracts the distributions for CLS from field data."
  [psi-result metric]
  (let [metrics (field-metrics psi-result metric)
        percentile (metrics "percentile")
        distributions (parse-distributions (metrics "distributions") metric)
        category (parse-category (metrics "category"))]
    (assoc distributions :category category :percentile percentile)))

(defn parse-results [psi-results]
  {:field-results {:cls (parse-field psi-results :cls)
                   :fcp (parse-field psi-results :fcp)
                   :fid (parse-field psi-results :fid)
                   :lcp (parse-field psi-results :lcp)}})

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
    (let [resp (client/get url)]
      (resp :body))
    (catch [:status 400] {:keys [body]}
      (assoc-in (parse-error body) [:error :type] "BAD_REQUEST"))
    (catch [:status 429] {:keys [body]}
      (assoc-in (parse-error body) [:error :type] "RATE_LIMIT"))))

(defn query
  "Makes an API request to the Google Page Speed Insights API for a requested URL."
  [url api-key]
  (let [request-url (str "https://www.googleapis.com/pagespeedonline/v5/runPagespeed?url=" url "&key=" api-key)
        resp (get-request request-url)]
    (parse-results resp)))
