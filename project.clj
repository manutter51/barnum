(defproject barnum "0.1.0-SNAPSHOT"
  :description "A simple event lib: define your own events, register handlers for them, and trigger them."
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [midje "1.6-beta1"]
                 [beanbag "0.2.3-SNAPSHOT"]]
  :profiles {:dev {
                   :plugins [[lein-midje "3.1.1"]]}})
