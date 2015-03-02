(ns barnum.api-test
  (:require [midje.sweet :refer :all]
            [barnum.api :refer :all]))

(def ^{:dynamic true} *some-state* (atom nil))

(fact "Event key must be a keyword"
      (add-event {} "e1" "Event 1")
      => (throws Exception "Event key must be a keyword")
      
      (add-event {} 'e1 "Event 1")
      => (throws Exception "Event key must be a keyword")
      
      (add-event {} #{:e1} "Event 1")
      => (throws Exception "Event key must be a keyword")
      
      (add-event {} [:e1] "Event 1")
      => (throws Exception "Event key must be a keyword")
      
      (add-event {} '(:e1) "Event 1")
      => (throws Exception "Event key must be a keyword"))

(fact "Event options must be either :min-handlers or max-handlers, followed by a number"
      (add-event {} :e1 :a-bad-param 0)
      => (throws Exception "Not a valid event option: (:a-bad-param)")

      (add-event {} :e1 :min-handlers)
      => (throws Exception "Invalid value for :min-handlers")

      (add-event {} :e1 :max-handlers)
      => (throws Exception "Invalid value for :max-handlers")

      (add-event {} :e1 :min-handlers :max-handlers)
      => (throws Exception "Invalid value for :min-handlers")

      (add-event {} :e1 :min-handlers "One")
      => (throws Exception "Invalid value for :min-handlers")

      (add-event {} :e1 :min-handlers [1])
      => (throws Exception "Invalid value for :min-handlers")

      (add-event {} :e1 :min-handlers #{1})
      => (throws Exception "Invalid value for :min-handlers")

      (add-event {} :e1 :min-handlers 3 :max-handlers 1)
      => (throws Exception ":min-handlers must be less than or equal to :max-handlers")

      (let [ctx (-> {}
                    (add-event :m1 :min-handlers 1)
                    (add-event :m2 :max-handlers 1)
                    (add-event :m3 :min-handlers 1 :max-handlers 1))
            registered-events (:barnum.events/registered-events ctx)]

        (get-in registered-events [:m1 :options])
        => (just {:min-handlers 1 :max-handlers Integer/MAX_VALUE})

        (get-in registered-events [:m2 :options])
        => (just {:min-handlers 0  :max-handlers 1})

        (get-in registered-events [:m3 :options])
        => (just {:min-handlers 1 :max-handlers 1})))



(fact "docs return docstring"
      (let [ctx (add-event {} :d1 "My docstring")
            ctx (add-event ctx :d2)]
        (docs ctx :d1)
        => (exactly "My docstring")

        (docs ctx :d2)
        => (exactly "Event d2 has no docs.")))





;; Adding handlers
(def ctx (atom {}))

(with-state-changes
  [(before :facts
           (reset! ctx (-> {}
                           (add-event :e1 "Event 1")
                           (add-event :e2 "Event 2")
                           (add-event :e3 "Event 3")
                           (add-event :e4 "Event 4")
                           (add-event :e5 "Event 5"))))]

  (fact "You can't add the same event twice"
        (add-event @ctx :e1 "Event 1")
        => (throws Exception "Duplicate event definition: :e1 Event 1"))

  (fact "You can add a handler to an event"
        (let [ctx @ctx
              ctx (add-handler ctx :e1 :h1 identity)
              registered-handlers (:barnum.events/registered-handlers ctx)
              handlers (:e1 registered-handlers)
              handler1 (first handlers)]
          (count handlers) => 1
          (first handler1) => :h1
          (second handler1) => identity))
  
  (fact "You cannot add a handler to an undefined event"
        (add-handler @ctx :no-such-event :h1 identity)
        => (throws Exception
                   "Handler :h1 cannot be used with unknown event :no-such-event"))

  (fact "You cannot add the same handler twice to the same event"
        (-> @ctx
            (add-handler :e1 :h1 identity)
            (add-handler :e1 :h1 identity))
        => (throws Exception "Duplicate event handler :h1 for event :e1"))

  (fact "You can add the same handler to more than one event"
        (let [ctx (add-handler @ctx #{:e1 :e2} :h1 identity)
              registered-handlers (:barnum.events/registered-handlers ctx)
              e1 (:e1 registered-handlers)
              e2 (:e2 registered-handlers)
              e1 (first e1)
              e2 (first e2)]
          (first e1) => :h1
          (first e2) => :h1
          (second e1) => identity
          (second e2) => identity))

  (fact "The event-key arg must be a keyword or a coll of keywords."
        (:barnum.events/registered-handlers (add-handler @ctx '(:e1 :e2) :h1 identity))
        => (contains {:e1 [[:h1 identity]] :e2 [[:h1 identity]]})

        (:barnum.events/registered-handlers (add-handler @ctx [:e1 :e2] :h1 identity))
        => (contains {:e1 [[:h1 identity]] :e2 [[:h1 identity]]})

        (add-handler @ctx "e1" :h1 identity)
        => (throws Exception
                   "Event key must be a keyword or a collection (vector/set/seq) of keywords.")
        (add-handler @ctx 'e1 :h1 identity)
        => (throws Exception
                   "Event key must be a keyword or a collection (vector/set/seq) of keywords."))
)

;; Managing handlers
(with-state-changes
  [(before :facts
           (reset! ctx (-> {}
                           (add-event :e1 "Event 1")
                           (add-handler :e1 :h1 identity)
                           (add-handler :e1 :h2 identity)
                           (add-handler :e1 :h3 identity)
                           (add-handler :e1 :h4 identity)
                           (add-handler :e1 :h5 identity))))]
  
  (fact "The handler-keys function returns the current list of handler
keys, in order"
        (handler-keys @ctx :e1)
        => (exactly [:h1 :h2 :h3 :h4 :h5]))

  (fact "Handlers are stored in the order they were added, by default"
        (vec (map first (:e1 (:barnum.events/registered-handlers @ctx))))
        => (exactly [:h1 :h2 :h3 :h4 :h5]))

  (fact "You can re-arrange the order of handlers using order-first"
        (let [ctx (order-first @ctx :e1 [:h3 :h2])
              registered-handlers (:barnum.events/registered-handlers ctx)]
          (vec (map first (:e1 registered-handlers)))
          => (exactly [:h3 :h2 :h1 :h4 :h5])))

  (fact "You can re-arrange the order of handlers using order-last"
        (let [ctx (order-last @ctx :e1 [:h3 :h2])
              registered-handlers (:barnum.events/registered-handlers ctx)]
          (vec (map first (:e1 registered-handlers)))
          => (exactly [:h1 :h4 :h5 :h3 :h2])))

  (fact "You can re-arrange the order of handlers using both order first and order last"
        (let [ctx (-> @ctx
                      (order-first :e1 [:h3])
                      (order-last :e1 [:h2]))
              registered-handlers (:barnum.events/registered-handlers ctx)]
          (vec (map first (:e1 registered-handlers)))
          => (exactly [:h3 :h1 :h4 :h5 :h2])))
)

;; Removing/replacing handlers
(with-state-changes
  [(before :facts
           (reset! ctx (-> {}
                            (add-event :e1 "Event 1")
                            (add-event :e2 "Event 2")
                            (add-event :e3 "Event 3")
                            (add-handler #{:e1 :e2 :e3} :h1 identity)
                            (add-handler #{:e1 :e3} :h2 identity)
                            (add-handler :e2 :h3 identity)
                            (add-handler :e2 :h4 identity)
                            (add-handler :e3 :h5 identity))))]
  
  (fact "You can remove one handler from one event without affecting other handlers or events"
        (let [ctx (remove-handler @ctx :e1 :h1)
              registered-handlers (:barnum.events/registered-handlers ctx)]
          (:e1 registered-handlers)
          => [[:h2 identity]]

          (:e2 registered-handlers)
          => [[:h1 identity]
              [:h3 identity]
              [:h4 identity]]

          (:e3 registered-handlers)
          => [[:h1 identity]
              [:h2 identity]
              [:h5 identity]]))

  (fact "You can remove handlers from multiple events"
        (let [ctx (remove-handler @ctx #{:e1 :e2} :h1)
              registered-handlers (:barnum.events/registered-handlers ctx)]
          (:e1 registered-handlers)
          => [[:h2 identity]]

          (:e2 registered-handlers)
          => [[:h3 identity]
              [:h4 identity]]

          (:e3 registered-handlers)
          => [[:h1 identity]
              [:h2 identity]
              [:h5 identity]]))

  (fact "It is not an error to try and remove a non-existent handler"
        (let [ctx (remove-handler @ctx :e1 :no-such-handler)
              registered-handlers (:barnum.events/registered-handlers ctx)]
          (:e1 registered-handlers)
          => [[:h1 identity]
              [:h2 identity]]))

  (fact "You can replace one handler with another"
        (let [new-fn (fn [args] (nil? args))
              ctx (replace-handler @ctx :e1 :h1 new-fn)
              registered-handlers (:barnum.events/registered-handlers ctx)]

          (:e1 registered-handlers)
          => [[:h1 new-fn]
              [:h2 identity]]))

  (fact "It is not an error to try and replace a handler that's not assigned"
        (let [new-fn (fn [args] (nil? args))
              ctx (replace-handler @ctx :e1 :no-such-handler new-fn)
              registered-handlers (:barnum.events/registered-handlers ctx)]
          (:e1 registered-handlers)
          => [[:h1 identity]
              [:h2 identity]]))
)

;; event handler check function
(with-state-changes
  [(before :facts
           (reset! ctx (-> {}
                           (add-event :e1 "Event 1"
                                      :max-handlers 2)
                           (add-event :e2 "Event 2")
                           (add-event :e3 "Event 2"
                                      :min-handlers 1)
                           (add-handler #{:e1 :e2} :h1 identity)
                           (add-handler :e1 :h2 identity)
                           (add-handler :e1 :h3 identity)
                           (add-handler :e2 :h4 identity))))]

  (fact "The check function returns correct messages for events with too many or too few handlers"
        (let [errors (check @ctx)]
          
          (:e1 errors)
          => ["The :e1 event can have at most 2 handler(s), has 3"]

          (:e2 errors) => nil

          (:e3 errors)
          => ["The :e3 event needs at least 1 handler(s), has 0"]))
  )

;; firing event handlers
;;   -- generic handlers

(defn sets-A-and-continues [ctx data]
  (ok (assoc data :a "A")))

(defn sets-B-and-jumps [ctx data]
  (ok-go :e2 (assoc data :b "B")))

(defn sets-C-and-continues [ctx data]
  (ok (assoc data :c "C")))

(defn sets-D-and-continues [ctx data]
  (ok (assoc data :d "D")))

(defn fails-and-sets-e1 [ctx data]
  (fail :error-1 "Error 1 happened" data))

(defn aborts-and-sets-e2 [ctx data]
  (fail-go :on-error-2 :error-2 "Error 2 happened" data))

(with-state-changes
  [(before :facts
           (reset! ctx (-> {}
                           (add-event :e1 "Event 1")
                           (add-event :e2 "Event 2")
                           (add-event :on-error-2 "Error event 2"))))]

  (fact
    "Handlers fire in the order they are defined."
    (let [ctx (-> @ctx
                  (add-handler :e1 :h1 sets-A-and-continues)
                  (add-handler :e1 :h2 sets-C-and-continues)
                  (add-handler :e1 :h3 sets-D-and-continues))]
      (:data (fire ctx :e1 {:key "value"}))
      => {:key "value" :a "A" :c "C" :d "D"}))



  (fact
    "When a handler returns ok-go, any remaining handlers are skipped, and the specified event fires"
    (let [ctx (-> @ctx
                  (add-handler :e1 :h1 sets-A-and-continues)
                  (add-handler :e1 :h2 sets-B-and-jumps)
                  (add-handler :e1 :h3 sets-C-and-continues)
                  (add-handler :e2 :h1 sets-D-and-continues))]
      (:data (fire ctx :e1 {:key "value"}))
      => {:key "value" :a "A" :b "B" :d "D"}))

  (fact
    "When a handler returns fail, any remaining handlers are skipped"
    (let [ctx (-> @ctx
                  (add-handler :e1 :h1 sets-A-and-continues)
                  (add-handler :e1 :h2 fails-and-sets-e1)
                  (add-handler :e1 :h3 sets-C-and-continues))]
      (:data (fire ctx :e1 {:key "value"}))
      => {:key "value" :a "A"}))
  )
