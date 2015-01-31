(ns barnum.events-test
  (:require [midje.sweet :refer :all]
            [barnum.events :refer :all]
            [beanbag.core :refer [ok fail skip]]))

(def ^{:dynamic true} *some-state* (atom nil))

(fact "about declaring events"
      (build-event-def
       :empty-event
       '())
      => {:key :empty-event
          :docstring nil
          :options {:defaults {}}
          :params nil}
      
      (build-event-def
       :empty-event
       '("a docstring"))
      => {:key :empty-event
          :docstring "a docstring"
          :options {:defaults {}}
          :params nil}
      
      (build-event-def
       :event-opt
       '({:min-handlers 1}))
      => {:key :event-opt
          :docstring nil
          :options {:defaults {}, :min-handlers 1}
          :params nil}
      
      (build-event-def
       :event-params
       '(:time :flux :foo))
      => {:key :event-params
          :docstring nil
          :options {:defaults {:flux nil, :foo nil, :time nil}}
          :params [:time :flux :foo]}

      (build-event-def
       :event-doc-opt
       '("a docstring"
         {:min-handlers 1}))
      => {:key :event-doc-opt
          :docstring "a docstring"
          :options {:defaults {},  :min-handlers 1}
          :params nil}
      
      (build-event-def
       :event-all
       '("a docstring"
         {:min-handlers 1}
         :time :flux :foo))
      => {:key :event-all
          :docstring "a docstring"
          :options {:defaults {:flux nil, :foo nil, :time nil},
                    :min-handlers 1}
          :params [:time :flux :foo]})

(fact "Event key must be a keyword"
      (register-event "e1" ["Event 1" :data])
      => (throws Exception "Event key must be a keyword")
      
      (register-event 'e1 ["Event 1" :data])
      => (throws Exception "Event key must be a keyword")
      
      (register-event #{:e1} ["Event 1" :data])
      => (throws Exception "Event key must be a keyword")
      
      (register-event [:e1] ["Event 1" :data])
      => (throws Exception "Event key must be a keyword")
      
      (register-event '(:e1) ["Event 1" :data])
      => (throws Exception "Event key must be a keyword"))

(fact "Event params must be keywords"
      (register-event :e9 ["Event 9" :data "foo"])
      => (throws Exception "Event params must all be keywords")
      
      (register-event :e9 ["Event 9" :data 'foo])
      => (throws Exception "Event params must all be keywords")
      
      (register-event :e9 ["Event 9" :data #{:foo}])
      => (throws Exception "Event params must all be keywords")
      
      (register-event :e9 ["Event 9" :data [:foo]])
      => (throws Exception "Event params must all be keywords")
      
      (register-event :e9 ["Event 9" :data '(:foo)])
      => (throws Exception "Event params must all be keywords"))

(fact "Events can use namespaced keywords from namespaces that do not exist"
      (keys (register-event :no.such.namespace/e10 ["Event 10" :data]))
      => (contains [:no.such.namespace/e10]))

;; Adding handlers
(with-state-changes [(before :facts
                             (do
                               (dosync (ref-set registered-handlers {}))
                               (reset! registered-events {})
                               (register-event :e1 ["Event 1" :data])
                               (register-event :e2 ["Event 2" :data])
                               (register-event :e3 ["Event 3" :data])
                               (register-event :e4 ["Event 4" :data])
                               (register-event :e5 ["Event 5" :data])))]
  
  (fact "You can't add the same event twice"
        (register-event :e1 ["Event 1" :data])
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
                              (register-event :e1 ["Event 1" :data])
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
                               (register-event :e1 ["Event 1" :data])
                               (register-event :e2 ["Event 2" :data])
                               (register-event :e3 ["Event 3" :data])
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
                               (register-event :e1 ["Event 1"
                                                    {:max-handlers 2}
                                                    :data])
                               (register-event :e2 ["Event 2" :data])
                               (register-event :e3 ["Event 2"
                                                    {:min-handlers 1} :data])
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

(defn appends-A-and-continues [args]
  (ok (swap! *some-state* str "A")))

(defn appends-B-and-continues [args]
  (ok (swap! *some-state* str "B")))

(defn appends-C-and-continues [args]
  (ok (swap! *some-state* str "C")))

(defn appends-D-and-stops [args]
  (ok :ok-stop (swap! *some-state* str "D")))

(defn always-fails [args]
  (fail "This handler always fails"))

(defn always-aborts [args]
  (fail :abort "This handler always aborts"))

(defn always-skips [args]
  (skip "This handler always skips"))

(with-state-changes [(before :facts
                             (do
                               (dosync
                                (ref-set registered-handlers {}))
                               (reset! registered-events {})
                               (reset! *some-state* "")
                               (register-event :e1 [ "Event 1" :data])))]
  
  (fact
   "Handlers fire in the order they are defined."
   (register-handler :e1 :h1 appends-A-and-continues)
   (register-handler :e1 :h2 appends-B-and-continues)
   (register-handler :e1 :h3 appends-C-and-continues)
   (let [handler-result (fire :e1 {:data ""})]
     @*some-state*
     => "ABC"))

  (fact
   "Events can be triggered asynchronously using future"
   (register-handler :e1 :h1 appends-A-and-continues)
   (register-handler :e1 :h2 appends-B-and-continues)
   (register-handler :e1 :h3 appends-C-and-continues)
   (let [handler-result (future (fire :e1 {:data ""}))
         blocked @handler-result]
     @*some-state*
     => "ABC"
     
     blocked
     => (exactly '([:ok "ABC"]
                     [:ok "AB"]
                     [:ok "A"]))))
  
  (fact
   "Handler results are returned in the inverse of the order they are defined."
   (register-handler :e1 :h1 appends-A-and-continues)
   (register-handler :e1 :h2 appends-B-and-continues)
   (register-handler :e1 :h3 appends-C-and-continues)
   (fire :e1 {:data ""})
   => (exactly '([:ok "ABC"]
                   [:ok "AB"]
                     [:ok "A"])))

  (fact
   "A handler can return a stop value that will prevent subsequent handlers from firing."
   (register-handler :e1 :h1 appends-A-and-continues)
   (register-handler :e1 :h2 appends-D-and-stops)
   (register-handler :e1 :h3 appends-C-and-continues)
   (let [handler-result (fire :e1 {:data ""})]
     @*some-state*
     => "AD"))

  (fact
   "A handler can return an abort result that will prevent subsequent handlers from firing."
   (register-handler :e1 :h1 appends-A-and-continues)
   (register-handler :e1 :h2 always-aborts)
   (register-handler :e1 :h3 appends-C-and-continues)
   (let [handler-result (fire :e1 {:data ""})]
     @*some-state*
     => "A"))
  
  (fact
   "A handler can return a skip result that will NOT prevent subsequent handlers from firing."
   (register-handler :e1 :h1 appends-A-and-continues)
   (register-handler :e1 :h2 always-skips)
   (register-handler :e1 :h3 appends-C-and-continues)
   (let [handler-result (fire :e1 {:data ""})]
     @*some-state*
     => "AC"))
  
  (fact
   "A handler can return a fail result that will NOT prevent subsequent handlers from firing."
   (register-handler :e1 :h1 appends-A-and-continues)
   (register-handler :e1 :h2 always-fails)
   (register-handler :e1 :h3 appends-C-and-continues)
   (let [handler-result (fire :e1 {:data ""})]
     @*some-state*
     => "AC"))
  )

