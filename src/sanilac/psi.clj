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

(defn parse-score-metric [score-object]
  {:title (score-object "title")
   :score (score-object "score")
   :value (score-object "numericValue")})

(defn parse-unused-js-object
  [js-object]
  {:url (js-object "url")
   :total-bytes (js-object "totalBytes")
   :wasted-bytes (js-object "wastedBytes")
   :sub-items (map (fn [sub-item]
                     {:source (sub-item "source")
                      :total-bytes (sub-item "sourceBytes")
                      :wasted-bytes (sub-item "sourceWastedBytes")})
                   (get-in js-object ["subItems" "items"]))})

(defn parse-duplicate-js-object
  [js-object]
  {:script (js-object "source")
   :wasted-bytes (js-object "wastedBytes")
   :sources (map (fn [source]
                   {:url (source "url")
                    :size-byte (source "sourceTransferBytes")})
                 (get-in js-object ["subItems" "items"]))})

(defn parse-legacy-js-object
  [js-object]
  {:script (js-object "url")
   :wasted-bytes (js-object "wastedBytes")
   :polyfills (map (fn [polyfill] (polyfill "signal"))
                   (get-in js-object ["subItems" "items"]))})

(defn parse-js-metric
  [js-object fn-parse-js]
  (let [score (parse-score-metric js-object)
        potential-bytes (get-in js-object ["details" "overallSavingsBytes"])
        scripts (get-in js-object ["details" "items"])]
    (assoc score
      :potential-bytes potential-bytes
      :scripts (map fn-parse-js scripts))))

(defn parse-lighthouse
  [psi-result]
  (let [json-result (json/read-str psi-result)
        audits (get-in json-result ["lighthouseResult" "audits"])

        offscreen-images (audits "offscreen-images")
        estimated-input-latency (audits "estimated-input-latency")
        uses-text-compression (audits "uses-text-compression")
        uses-webp-images (audits "uses-webp-images")
        uses-rel-preconnect (audits "uses-rel-preconnect")
        uses-optimized-images (audits "uses-optimized-images")
        uses-rel-preload (audits "uses-rel-preload")
        font-display (audits "font-display")
        uses-long-cache-ttl (audits "uses-long-cache-ttl")
        bootup-time (audits "bootup-time")                  ;; Go deep into this one.
        first-cpu-idle (audits "first-cpu-idle")
        render-blocking-resources (audits "render-blocking-resources")
        total-byte-weight (audits "total-byte-weight")
        layout-shift-elements (audits "layout-shift-elements")
        resource-summary (audits "resource-summary")
        diagnostics (audits "diagnostics")
        mainthread-work-breakdown (audits "mainthread-work-breakdown")
        third-party-summary (audits "third-party-summary")
        dom-size (audits "dom-size")
        unused-css-rules (audits "unused-css-rules")
        server-response-time (audits "server-response-time")
        uses-passive-event-listeners (audits "uses-passive-event-listeners")
        network-server-latency (audits "network-server-latency")
        critical-request-chains (audits "critical-request-chains")
        main-thread-tasks (audits "main-thread-tasks")
        unminified-css (audits "unminified-css")
        network-requests (audits "network-requests")
        no-document-write (audits "no-document-write")
        redirects (audits "redirects")
        efficient-animated-content (audits "efficient-animated-content")
        metrics (audits "metrics")
        long-tasks (audits "long-tasks")
        uses-responsive-images (audits "uses-responsive-images")
        lcp-element (audits "largest-contentful-paint-element")
        max-potential-fid (audits "max-potential-fid")]
    {:lighthouse {
                  :scores    {:cls (parse-score-metric (audits "cumulative-layout-shift"))
                              :fcp (parse-score-metric (audits "first-contentful-paint"))
                              :fmp (parse-score-metric (audits "first-meaningful-paint"))
                              :lcp (parse-score-metric (audits "largest-contentful-paint"))
                              :si  (parse-score-metric (audits "speed-index"))
                              :tbt (parse-score-metric (audits "total-blocking-time"))
                              :tti (parse-score-metric (audits "interactive"))}
                  :js-audits {:unused-javascript          (parse-js-metric
                                                            (audits "unused-javascript")
                                                            parse-unused-js-object)
                              :duplicated-javascript      (parse-js-metric
                                                            (audits "duplicated-javascript")
                                                            parse-duplicate-js-object)
                              ;; TODO: Find an example of this working.
                              :unminified-javascript      (parse-js-metric
                                                            (audits "unminified-javascript")
                                                            parse-unused-js-object)
                              ;; TODO: Find an example of this working.
                              :large-javascript-libraries (parse-js-metric
                                                            (audits "large-javascript-libraries")
                                                            parse-unused-js-object)
                              :legacy-javascript          (parse-js-metric
                                                            (audits "legacy-javascript")
                                                            parse-legacy-js-object)}}}))

(defn parse-results [psi-results]
  {:field-results {:cls (parse-field psi-results :cls)
                   :fcp (parse-field psi-results :fcp)
                   :fid (parse-field psi-results :fid)
                   :lcp (parse-field psi-results :lcp)}
   :lighthouse-results (parse-lighthouse psi-results)})

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

(defn psi-url
  "Creates the URL required to query the Page Speed Insights API."
  [url api-key]
  (str "https://www.googleapis.com/pagespeedonline/v5/runPagespeed?url=" url "&key=" api-key))

(defn query
  "Makes an API request to the Google Page Speed Insights API for a requested URL."
  [url api-key]
  (let [resp (get-request (psi-url url api-key))]
    (parse-results resp)))
