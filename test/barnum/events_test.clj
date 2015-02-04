(ns barnum.events-test
  (:require [midje.sweet :refer :all]
            [barnum.events :refer :all]
            [barnum.results :refer [ok ok-go fail fail-go]]))

(fact "build-event-def returns appropriate event structures"
      (build-event-def
       :empty-event
       [])
      => {:key :empty-event
          :docstring nil
          :options {:max-handlers Integer/MAX_VALUE, :min-handlers 0}}
      
      (build-event-def
       :empty-event
       ["a docstring"])
      => {:key :empty-event
          :docstring "a docstring"
          :options {:max-handlers Integer/MAX_VALUE, :min-handlers 0}}
      
      (build-event-def
       :event-opt
       [:min-handlers 1])
      => {:key :event-opt
          :docstring nil
          :options {:max-handlers Integer/MAX_VALUE, :min-handlers 1}}
      

      (build-event-def
       :event-doc-opt
       '["a docstring" :min-handlers 1])
      => {:key :event-doc-opt
          :docstring "a docstring"
          :options {:max-handlers Integer/MAX_VALUE, :min-handlers 1}})

(fact "Event key must be a keyword"
      (register-event "e1" ["Event 1"])
      => (throws Exception "Event key must be a keyword")
      
      (register-event 'e1 ["Event 1"])
      => (throws Exception "Event key must be a keyword")
      
      (register-event #{:e1} ["Event 1"])
      => (throws Exception "Event key must be a keyword")
      
      (register-event [:e1] ["Event 1"])
      => (throws Exception "Event key must be a keyword")
      
      (register-event '(:e1) ["Event 1"])
      => (throws Exception "Event key must be a keyword"))

(fact "Events can use namespaced keywords from namespaces that do not exist"
      (keys (register-event :no.such.namespace/e10 ["Event 10"]))
      => (contains [:no.such.namespace/e10]))

;; Setting validation functions

(with-state-changes [(before :facts
                             (do
                               (dosync (ref-set registered-handlers {}))
                               (reset! registered-events {})
                               (register-event :e1 ["Event 1"])))]

    (fact "The event must be referenced by an event key"
          (set-validation-fn! {:e1 {:validation-fn nil }} identity)
          => (throws Exception "Cannot set validation function: {:e1 {:validation-fn nil}} is not a keyword"))

    (fact "You can set the validation function on an event"
          (set-validation-fn! :e1 identity)

          @registered-events
          => {:e1 {:key :e1
                   :docstring "Event 1"
                   :options {:min-handlers 0 :max-handlers Integer/MAX_VALUE}
                   :validation-fn identity}})

    (fact "You can reset the validation function to nil"
          (set-validation-fn! :e1 identity)
          (set-validation-fn! :e1 nil)

          @registered-events
          => {:e1 {:key :e1
                   :docstring "Event 1"
                   :options {:min-handlers 0 :max-handlers Integer/MAX_VALUE}
                   :validation-fn nil}})

    (fact "If you have no validation fn, execution will proceed as though validation had succeeded."
          (validate-args :e1 {:key "value"})
          => {:key "value"})

    (fact "If you set a new validation fn, it will replace the old one"
          (set-validation-fn! :e1 (fn [& more] (throw (Exception. "Old"))))
          (set-validation-fn! :e1 (fn [& more] (throw (Exception. "New"))))

          (validate-args :e1 {:key "value"})
          => (throws Exception "New")))

;; Adding handlers
(with-state-changes [(before :facts
                             (do
                               (dosync (ref-set registered-handlers {}))
                               (reset! registered-events {})
                               (register-event :e1 ["Event 1"])
                               (register-event :e2 ["Event 2"])
                               (register-event :e3 ["Event 3"])
                               (register-event :e4 ["Event 4"])
                               (register-event :e5 ["Event 5"])))]

  (fact "You can't add the same event twice"
        (register-event :e1 ["Event 1"])
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
        => (throws Exception
                   "Cannot register handler :h1 for unknown event :no-such-event"))

  (fact "You cannot add the same handler to the same event twice"
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
        => (throws Exception
                   "Event key must be a keyword or a set of keywords.")
        (register-handler [:e1 :e2] :h1 identity)
        => (throws Exception
                   "Event key must be a keyword or a set of keywords.")
        (register-handler "e1" :h1 identity)
        => (throws Exception
                   "Event key must be a keyword or a set of keywords.")
        (register-handler 'e1 :h1 identity)
        => (throws Exception
                   "Event key must be a keyword or a set of keywords."))
)

;; Managing handlers
(with-state-changes [(before :facts
                             (do
                              (dosync (ref-set registered-handlers {}))
                              (reset! registered-events {})
                              (register-event :e1 ["Event 1"])
                              (register-handler :e1 :h1 identity)
                              (register-handler :e1 :h2 identity)
                              (register-handler :e1 :h3 identity)
                              (register-handler :e1 :h4 identity)
                              (register-handler :e1 :h5 identity)))]
  
  (fact "The handler-keys function returns the current list of handler
keys, in order"
        (handler-keys :e1)
        => (exactly [:h1 :h2 :h3 :h4 :h5]))

  (fact "Handlers are stored in the order they were added, by default"
        (vec (map first (:e1 @registered-handlers)))
        => (exactly [:h1 :h2 :h3 :h4 :h5]))

  (fact "You can re-arrange the order of handlers using order-first"
        (order-first :e1 [:h3 :h2])
        (vec (map first (:e1 @registered-handlers)))
        => (exactly [:h3 :h2 :h1 :h4 :h5]))

  (fact "You can re-arrange the order of handlers using order-last"
        (order-last :e1 [:h3 :h2])
        (vec (map first (:e1 @registered-handlers)))
        => (exactly [:h1 :h4 :h5 :h3 :h2]))

  (fact "You can re-arrange the order of handlers using both order first and order last"
        (order-first :e1 [:h3])
        (order-last :e1 [:h2])
        (vec (map first (:e1 @registered-handlers)))
        => (exactly [:h3 :h1 :h4 :h5 :h2]))
)

;; Removing/replacing handlers
(with-state-changes [(before :facts
                             (do
                               (dosync (ref-set registered-handlers {}))
                               (reset! registered-events {})
                               (register-event :e1 ["Event 1"])
                               (register-event :e2 ["Event 2"])
                               (register-event :e3 ["Event 3"])
                               (register-handler #{:e1 :e2 :e3} :h1 identity)
                               (register-handler #{:e1 :e3} :h2 identity)
                               (register-handler :e2 :h3 identity)
                               (register-handler :e2 :h4 identity)
                               (register-handler :e3 :h5 identity)))]
  
  (fact "You can remove one handler from one event without affecting other handlers or events"
        (remove-handler :e1 :h1)

        (:e1 @registered-handlers)
        => [[:h2 identity]]

        (:e2 @registered-handlers)
        => [[:h1 identity]
            [:h3 identity]
            [:h4 identity]]

        (:e3 @registered-handlers)
        => [[:h1 identity]
            [:h2 identity]
            [:h5 identity]])

  (fact "You can remove handlers from multiple events"
        (remove-handler #{:e1 :e2} :h1)
        
        (:e1 @registered-handlers)
        => [[:h2 identity]]
        
        (:e2 @registered-handlers)
        => [[:h3 identity]
            [:h4 identity]]
        
        (:e3 @registered-handlers)
        => [[:h1 identity]
            [:h2 identity]
            [:h5 identity]])

  (fact "It is not an error to try and remove a non-existent handler"
        (remove-handler :e1 :no-such-handler)

        (:e1 @registered-handlers)
        => [[:h1 identity]
            [:h2 identity]])

  (fact "You can replace one handler with another"
        (let [new-fn (fn [args] (nil? args))]
          (replace-handler :e1 :h1 new-fn)

          (:e1 @registered-handlers)
          => [[:h1 new-fn]
              [:h2 identity]]))

  (fact "It is not an error to try and replace a handler that's not assigned"
        (let [new-fn (fn [args] (nil? args))]
          (replace-handler :e1 :no-such-handler new-fn)
          (:e1 @registered-handlers)
          => [[:h1 identity]
              [:h2 identity]]))
)

;; event handler check function
(with-state-changes [(before :facts
                             (do
                               (dosync (ref-set registered-handlers {}))
                               (reset! registered-events {})
                               (register-event :e1 ["Event 1" :max-handlers 2])
                               (register-event :e2 ["Event 2"])
                               (register-event :e3 ["Event 2" :min-handlers 1])
                               (register-handler #{:e1 :e2} :h1 identity)
                               (register-handler :e1 :h2 identity)
                               (register-handler :e1 :h3 identity)
                               (register-handler :e2 :h4 identity)))]

  (fact "The check function returns correct messages for events with too many or too few handlers"
        (let [errors (check)]
          
          (:e1 errors)
          => ["The :e1 event can have at most 2 handler(s), has 3"]

          (:e2 errors) => nil

          (:e3 errors)
          => ["The :e3 event needs at least 1 handler(s), has 0"]))
  )

;; firing event handlers
;;   -- generic handlers

(defn sets-A-and-continues [data]
  (ok (assoc data :a "A")))

(defn sets-B-and-jumps [data]
  (ok-go :e2 (assoc data :b "B")))

(defn sets-C-and-continues [data]
  (ok (assoc data :c "C")))

(defn sets-D-and-continues [data]
  (ok (assoc data :d "D")))

(defn fails-and-sets-e1 [data]
  (fail "Error 1 happened" (assoc data :e1 "Error 1")))

(defn aborts-and-sets-e2 [data]
  (fail-go :on-error-2 "Error 2 happened" (assoc data :e2 "Error 2")))

(def initial-data {:key "value"})

(with-state-changes
  [(before :facts
           (do
             (dosync
               (ref-set registered-handlers {}))
             (reset! registered-events {})
             (reset! *some-state* "")
             (register-event :e1 ["Event 1"])
             (register-event :e2 ["Event 2"])
             (register-event :on-error-2 ["Error handler 2"])))]

  (fact
    "Handlers fire in the order they are defined."
    (register-handler :e1 :h1 sets-A-and-continues)
    (register-handler :e1 :h2 sets-C-and-continues)
    (register-handler :e1 :h3 sets-D-and-continues)

    (fire :e1 initial-data)
    => {:key "value" :a "A" :c "C" :d "D" :barnum.events/log [[:e1 :h1] [:e1 :h2] [:e1 :h3]]})

  (fact
    "When a handler returns ok-go, any remaining handlers are skipped, and the specified event fires"
    (register-handler :e1 :h1 sets-A-and-continues)
    (register-handler :e1 :h2 sets-B-and-jumps)
    (register-handler :e1 :h3 sets-C-and-continues)
    (register-handler :e2 :h1 sets-D-and-continues)

    (fire :e1 initial-data)
    => {:key "value" :a "A" :b "B" :d "D" :barnum.events/log [[:e1 :h1] [:e1 :h2] [:e2 :h1]]})

  (fact
    "When a handler returns fail, any remaining handlers are skipped"
    (register-handler :e1 :h1 sets-A-and-continues)
    (register-handler :e1 :h2 fails-and-sets-e1)
    (register-handler :e1 :h3 sets-C-and-continues)

    (fire :e1 initial-data)
    => {:key "value" :a "A" :e1 "Error 1" :barnum.events/log [[:e1 :h1] [:e1 :h2]] :barnum.errors/errors [[:e1 :h2 "Error 1 happened"]]})

  )