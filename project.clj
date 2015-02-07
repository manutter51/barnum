(defproject barnum "0.2.0-SHAPSHOT"
  :description "A simple event lib: define your own events, register
handlers for them, and fire them."
  :url "http://github.com/manutter51/barnum"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]]
  :profiles {:dev {:dependencies [[midje "1.6.3"]]
                   :plugins [[lein-midje "3.1.3"]]}})
