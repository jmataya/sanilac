(defproject sanilac "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [environ "1.2.0"]
                 [clj-http "3.10.3"]
                 [slingshot "0.12.2"]]
  :main ^:skip-aot sanilac.core
  :target-path "target/%s"
  :plugins [[lein-environ "1.2.0"]]
  :profiles {:uberjar {:aot :all}
             :dev [:project/dev :profiles/dev]
             :profiles/dev {}
             :project/dev {}})
