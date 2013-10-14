(ns barnum.events-test
  (:refer-clojure :exclude [declare compile])
  (:require [midje.sweet :refer :all]
            [barnum.events :refer :all]))

(fact "about declaring events"
      (build-event-def
       :empty-event
       '()) => {:key :empty-event
                :docstring nil
                :options nil
                :params nil}
       (build-event-def
        :empty-event
        '("a docstring")) => {:key :empty-event
                              :docstring "a docstring"
                              :options nil
                              :params nil}
        (build-event-def
         :event-opt
         '({:min-handlers 1})) => {:key :event-opt
                                   :docstring nil
                                   :options {:min-handlers 1}
                                   :params nil}
         (build-event-def
          :event-params
          '(:time :flux :foo)) => {:key :event-params
                                   :docstring nil
                                   :options nil
                                   :params [:time :flux :foo]}
          (build-event-def
           :event-doc-opt
           '("a docstring"
             {:min-handlers 1})) => {:key :event-doc-opt
                                     :docstring "a docstring"
                                     :options {:min-handlers 1}
                                     :params nil}
             (build-event-def
              :event-all
              '("a docstring"
                {:min-handlers 1}
                :time :flux :foo)) => {:key :event-all
                                       :docstring "a docstring"
                                       :options {:min-handlers 1}
                                       :params [:time :flux :foo]})

