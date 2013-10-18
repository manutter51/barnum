(ns barnum.events-test
  (:refer-clojure :exclude [declare compile])
  (:require [midje.sweet :refer :all]
            [barnum.events :refer :all]))

(fact "about declaring events"
      (build-event-def
       :empty-event
       '())
      => {:key :empty-event
          :docstring nil
          :options nil
          :params nil}
      
      (build-event-def
       :empty-event
       '("a docstring"))
      => {:key :empty-event
          :docstring "a docstring"
          :options nil
          :params nil}
      
      (build-event-def
       :event-opt
       '({:min-handlers 1}))
      => {:key :event-opt
          :docstring nil
          :options {:min-handlers 1}
          :params nil}
      
      (build-event-def
       :event-params
       '(:time :flux :foo))
      => {:key :event-params
          :docstring nil
          :options nil
          :params [:time :flux :foo]}

      (build-event-def
       :event-doc-opt
       '("a docstring"
         {:min-handlers 1}))
      => {:key :event-doc-opt
          :docstring "a docstring"
          :options {:min-handlers 1}
          :params nil}
      
      (build-event-def
       :event-all
       '("a docstring"
         {:min-handlers 1}
         :time :flux :foo))
      => {:key :event-all
          :docstring "a docstring"
          :options {:min-handlers 1}
          :params [:time :flux :foo]})

(fact "Event key must be a keyword"
      (register-event "e1" "Event 1" :data)
      => (throws Exception "Event key must be a keyword")
      
      (register-event 'e1 "Event 1" :data)
      => (throws Exception "Event key must be a keyword")
      
      (register-event #{:e1} "Event 1" :data)
      => (throws Exception "Event key must be a keyword")
      
      (register-event [:e1] "Event 1" :data)
      => (throws Exception "Event key must be a keyword")
      
      (register-event '(:e1) "Event 1" :data)
      => (throws Exception "Event key must be a keyword"))

(fact "Event params must be keywords"
      (register-event :e9 "Event 9" :data "foo")
      => (throws Exception "Event params must all be keywords")
      
      (register-event :e9 "Event 9" :data 'foo)
      => (throws Exception "Event params must all be keywords")
      
      (register-event :e9 "Event 9" :data #{:foo})
      => (throws Exception "Event params must all be keywords")
      
      (register-event :e9 "Event 9" :data [:foo])
      => (throws Exception "Event params must all be keywords")
      
      (register-event :e9 "Event 9" :data '(:foo))
      => (throws Exception "Event params must all be keywords"))

(with-state-changes [(before :facts
                             (do
                               (register-event :e1 "Event 1" :data)
                               (register-event :e2 "Event 2" :data)
                               (register-event :e3 "Event 3" :data)
                               (register-event :e4 "Event 4" :data)
                               (register-event :e5 "Event 5" :data)))
                     (after :facts
                            (do
                              (dosync (ref-set registered-handlers {}))
                              (reset! registered-events {})))]
  
  (fact "You can't add the same event twice"
        (register-event :e1 "Event 1" :data)
        => (throws Exception "Duplicate event definition: :e1 Event 1"))
  
  (fact "You can add a handler to an event"
        (register-handler :e1 :h1 identity)
        (let [handlers (:e1 @registered-handlers)
              handler1 (first handlers)]
          (count handlers) => 1
          (first handler1) => :h1
          (second handler1) => identity))
  
  (fact "You cannot add a handler to an undefined event"
        (register-handler :no-such-event :h1 identity)
        => (throws Exception "Cannot register handler :h1 for unknown event :no-such-event"))

  (fact "You cannot add the same handler twice"
        (register-handler :e1 :h1 identity)
        (register-handler :e1 :h1 identity)
        => (throws Exception "Duplicate event handler :h1 for event :e1"))

  (fact "You can add the same handler to more than one event"
        (register-handler #{:e1 :e2} :h1 identity)
        (let [e1 (:e1 @registered-handlers)
              e2 (:e2 @registered-handlers)
              e1 (first e1)
              e2 (first e2)]
          (first e1) => :h1
          (first e2) => :h1
          (second e1) => identity
          (second e2) => identity))

  (fact "The event-key arg must be a keyword or a set of keywords."
        (register-handler '(:e1 :e2) :h1 identity)
        => (throws Exception "Event key must be a keyword or a set of keywords.")
        (register-handler [:e1 :e2] :h1 identity)
        => (throws Exception "Event key must be a keyword or a set of keywords.")
        (register-handler "e1" :h1 identity)
        => (throws Exception "Event key must be a keyword or a set of keywords.")
        (register-handler 'e1 :h1 identity)
        => (throws Exception "Event key must be a keyword or a set of keywords."))

  (fact "Handlers are stored in the order they were added, by default"
        )

  (fact "You can re-arrange the order of handlers using order-first"
        )

  (fact "You can re-arrange the order of handlers using order-last"
        )


  
  )
