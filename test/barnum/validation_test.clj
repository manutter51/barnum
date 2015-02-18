(ns barnum.validation-test
  (:require [midje.sweet :refer :all]
            [barnum.api :refer :all]
            [barnum.events.validation :refer :all]))

(defn do-nothing-handler [ctx args]
  (ok ctx))

;; TODO Refactor this to use separate fn for setting validation fn

#_(with-state-changes [(before :facts
                             (do
                               (dosync (ref-set registered-handlers {}))
                               (reset! registered-events {})))]

  (fact "The require-all function fails unless all params are present and have non-nil values."
        (add-event :e1
                   "Event with params"
                   {:validation-fn require-all}
                   :param1 :param2)
        (add-handler :e1 :h1 do-nothing-handler)
        (fire :e1 :param1 :foo)
        => (exactly [:fail '("Required value missing for key :param2")]))
  
  (fact "The require-all function passes when all params are present with non-nil values"
        (add-event :e1
                   "Event with params"
                   {:validation-fn require-all}
                   :param1 :param2)
        (add-handler :e1 :h1 do-nothing-handler)
        (fire :e1 :param1 :foo :param2 :bar)
        => (exactly '([:ok nil])))

  (fact "The restrict-all function fails if any keys in the args are not present in the params list."
        (add-event :e1
                   "Event with params"
                   {:validation-fn restrict-all}
                   :param1)
        (add-handler :e1 :h1 do-nothing-handler)
        (fire :e1 :param1 :foo :param2 :bar)
        => (exactly [:fail '("Unexpected key :param2 in arg list")]))

  (fact "The restrict-all function passes if all keys in the args are present in the params list."
        (add-event :e1
                   "Event with params"
                   {:validation-fn restrict-all}
                   :param1 :param2)
        (add-handler :e1 :h1 do-nothing-handler)
        (fire :e1 :param1 :foo :param2 :bar)
        => (exactly '([:ok nil])))

  (fact "The restrict-all function passes if some keys in the params list are not present in the args list"
        (add-event :e1
                   "Event with params"
                   {:validation-fn restrict-all}
                   :param1 :param2 :param3)
        (add-handler :e1 :h1 do-nothing-handler)
        (fire :e1 :param1 :foo :param2 :bar)
        => (exactly '([:ok nil])))
)
