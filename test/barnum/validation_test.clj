(ns barnum.validation-test
  (:require [midje.sweet :refer :all]
            [barnum.api :refer :all]
            [barnum.events :refer [registered-events registered-handlers]]
            [barnum.events.validation :refer :all]
            [beanbag.core :refer [ok fail skip]]))

(defn do-nothing-handler [args]
  (ok))

(with-state-changes [(before :facts
                             (do
                               (dosync (ref-set registered-handlers {}))
                               (reset! registered-events {})))]

  (fact "The require-all function fails unless all params are present"
        (add-event :e1
                   "Event with params"
                   {:validation-fn require-all}
                   :param1 :param2)
        (add-handler :e1 :h1 do-nothing-handler)
        @(fire :e1 :param1 :foo)
        => (exactly '([:fail '(:param2)])))
  )

