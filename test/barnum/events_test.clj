(ns barnum.events-test
  (:refer-clojure :exclude [declare compile])
  (:require [midje.sweet :refer :all]
            [barnum.events :refer :all]))

(fact "about declaring event dictionaries"
      (let [d (declare {:app-start "First event to fire when the app starts" :app-stop "Last event fired before shutdown."})]
        (:event-keys (meta d)) => #{:app-start :app-stop}
        (:compiled (meta d)) => falsey
        (help d :app-start) => "First event to fire when the app starts"))
