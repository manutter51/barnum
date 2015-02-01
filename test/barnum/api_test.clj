(ns barnum.api-test
  (:require [midje.sweet :refer :all]
            [barnum.api :refer :all]
            [barnum.events :refer [registered-events registered-handlers]]))

(def ^{:dynamic true} *some-state* (atom nil))

(fact "Event key must be a keyword"
      (add-event "e1" "Event 1")
      => (throws Exception "Event key must be a keyword")
      
      (add-event 'e1 "Event 1")
      => (throws Exception "Event key must be a keyword")
      
      (add-event #{:e1} "Event 1")
      => (throws Exception "Event key must be a keyword")
      
      (add-event [:e1] "Event 1")
      => (throws Exception "Event key must be a keyword")
      
      (add-event '(:e1) "Event 1")
      => (throws Exception "Event key must be a keyword"))

(fact "Event options must be either :min-handlers or max-handlers, followed by a number"
      (add-event :e1 :a-bad-param 0)
      => (throws Exception "Not a valid event option: (:a-bad-param)")

      (add-event :e1 :min-handlers)
      => (throws Exception "Invalid value for :min-handlers")

      (add-event :e1 :max-handlers)
      => (throws Exception "Invalid value for :max-handlers")

      (add-event :e1 :min-handlers :max-handlers)
      => (throws Exception "Invalid value for :min-handlers")

      (add-event :e1 :min-handlers "One")
      => (throws Exception "Invalid value for :min-handlers")

      (add-event :e1 :min-handlers [1])
      => (throws Exception "Invalid value for :min-handlers")

      (add-event :e1 :min-handlers #{1})
      => (throws Exception "Invalid value for :min-handlers")

      (add-event :e1 :min-handlers 3 :max-handlers 1)
      => (throws Exception ":min-handlers must be less than or equal to :max-handlers")

      (add-event :m1 :min-handlers 1)
      (add-event :m2 :max-handlers 1)
      (add-event :m3 :min-handlers 1 :max-handlers 1)

      (get-in @registered-events [:m1 :options])
      => (just {:min-handlers 1 :max-handlers Integer/MAX_VALUE})

      (get-in @registered-events [:m2 :options])
      => (just {:min-handlers 0  :max-handlers 1})

      (get-in @registered-events [:m3 :options])
      => (just {:min-handlers 1 :max-handlers 1}))

;; Adding handlers
(with-state-changes
  [(before :facts
           (do
             (dosync (ref-set registered-handlers {}))
             (reset! registered-events {})
             (add-event :e1 "Event 1")
             (add-event :e2 "Event 2")
             (add-event :e3 "Event 3")
             (add-event :e4 "Event 4")
             (add-event :e5 "Event 5")))]
  
  (fact "You can't add the same event twice"
        (add-event :e1 "Event 1")
        => (throws Exception "Duplicate event definition: :e1 Event 1"))
  
  (fact "You can add a handler to an event"
        (add-handler :e1 :h1 identity)
        (let [handlers (:e1 @registered-handlers)
              handler1 (first handlers)]
          (count handlers) => 1
          (first handler1) => :h1
          (second handler1) => identity))
  
  (fact "You cannot add a handler to an undefined event"
        (add-handler :no-such-event :h1 identity)
        => (throws Exception
                   "Cannot register handler :h1 for unknown event :no-such-event"))

  (fact "You cannot add the same handler twice to the same event"
        (add-handler :e1 :h1 identity)
        (add-handler :e1 :h1 identity)
        => (throws Exception "Duplicate event handler :h1 for event :e1"))

  (fact "You can add the same handler to more than one event"
        (add-handler #{:e1 :e2} :h1 identity)
        (let [e1 (:e1 @registered-handlers)
              e2 (:e2 @registered-handlers)
              e1 (first e1)
              e2 (first e2)]
          (first e1) => :h1
          (first e2) => :h1
          (second e1) => identity
          (second e2) => identity))

  (fact "The event-key arg must be a keyword or a set of keywords."
        (add-handler '(:e1 :e2) :h1 identity)
        => (throws Exception
                   "Event key must be a keyword or a set of keywords.")
        (add-handler [:e1 :e2] :h1 identity)
        => (throws Exception
                   "Event key must be a keyword or a set of keywords.")
        (add-handler "e1" :h1 identity)
        => (throws Exception
                   "Event key must be a keyword or a set of keywords.")
        (add-handler 'e1 :h1 identity)
        => (throws Exception
                   "Event key must be a keyword or a set of keywords."))
)

;; Managing handlers
(with-state-changes
  [(before :facts
           (do
             (dosync (ref-set registered-handlers {}))
             (reset! registered-events {})
             (add-event :e1 "Event 1")
             (add-handler :e1 :h1 identity)
             (add-handler :e1 :h2 identity)
             (add-handler :e1 :h3 identity)
             (add-handler :e1 :h4 identity)
             (add-handler :e1 :h5 identity)))]
  
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
(with-state-changes
  [(before :facts
           (do
             (dosync (ref-set registered-handlers {}))
             (reset! registered-events {})
             (add-event :e1 "Event 1")
             (add-event :e2 "Event 2")
             (add-event :e3 "Event 3")
             (add-handler #{:e1 :e2 :e3} :h1 identity)
             (add-handler #{:e1 :e3} :h2 identity)
             (add-handler :e2 :h3 identity)
             (add-handler :e2 :h4 identity)
             (add-handler :e3 :h5 identity)))]
  
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
(with-state-changes
  [(before :facts
           (do
             (dosync (ref-set registered-handlers {}))
             (reset! registered-events {})
             (add-event :e1 "Event 1"
                        :max-handlers 2)
             (add-event :e2 "Event 2")
             (add-event :e3 "Event 2"
                        :min-handlers 1)
             (add-handler #{:e1 :e2} :h1 identity)
             (add-handler :e1 :h2 identity)
             (add-handler :e1 :h3 identity)
             (add-handler :e2 :h4 identity)))]

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

(defn appends-A-and-continues [args]
  (ok (swap! *some-state* str "A")))

(defn appends-B-and-continues [args]
  (ok (swap! *some-state* str "B")))

(defn appends-C-and-continues [args]
  (ok (swap! *some-state* str "C")))

#_(defn appends-D-and-stops [args]
  (ok :ok-stop (swap! *some-state* str "D")))

(defn always-fails [args]
  (fail "This handler always fails"))

(with-state-changes [(before :facts
                             (do
                               (dosync
                                (ref-set registered-handlers {}))
                               (reset! registered-events {})
                               (reset! *some-state* "")
                               (add-event :e1 "Event 1")))]
  
  (fact
   "Handlers fire in the order they are defined."
   (add-handler :e1 :h1 appends-A-and-continues)
   (add-handler :e1 :h2 appends-B-and-continues)
   (add-handler :e1 :h3 appends-C-and-continues)
   (let [handler-result (fire :e1 :data "")]
     @*some-state*
     => "ABC"))

  #_(fact
   "A handler can return a stop value that will prevent subsequent handlers from firing."
   (add-handler :e1 :h1 appends-A-and-continues)
   (add-handler :e1 :h2 appends-D-and-stops)
   (add-handler :e1 :h3 appends-C-and-continues)
   (let [handler-result (fire :e1 :data "")]
     @*some-state*
     => "AD"))

  #_(fact
   "A handler can return an abort result that will prevent subsequent handlers from firing."
   (add-handler :e1 :h1 appends-A-and-continues)
   (add-handler :e1 :h2 always-aborts)
   (add-handler :e1 :h3 appends-C-and-continues)
   (let [handler-result (fire :e1 :data "")]
     @*some-state*
     => "A"))
  
  #_(fact
   "A handler can return a skip result that will NOT prevent subsequent handlers from firing."
   (add-handler :e1 :h1 appends-A-and-continues)
   (add-handler :e1 :h2 always-skips)
   (add-handler :e1 :h3 appends-C-and-continues)
   (let [handler-result (fire :e1 :data "")]
     @*some-state*
     => "AC"))
  
  #_(fact
   "A handler can return a fail result that will NOT prevent subsequent handlers from firing."
   (add-handler :e1 :h1 appends-A-and-continues)
   (add-handler :e1 :h2 always-fails)
   (add-handler :e1 :h3 appends-C-and-continues)
   (let [handler-result (fire :e1 :data "")]
     @*some-state*
     => "AC"))
  )

(defn adds-1-and-fires-e2 [args]
  (ok-go :e2 (swap! *some-state* inc)))

(defn multiplies-by-to2-and-fires-e3 [args]
  (ok-go :e3 (swap! *some-state* * 2)))

(defn subtracts-1-and-fires-e4 [args]
  (ok-go :e4 (swap! *some-state* dec)))