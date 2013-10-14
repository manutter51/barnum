(ns barnum.events)

(def registered-events (atom {}))
(def registered-handlers (atom {}))
(def tmp-handlers (atom {}))

(defn- ev-extract [fn params]
  (let [classified (group-by fn params)
        string (first (classified true))
        params (classified false)]
    [string params]))

(defn- ev-get-docstring [params]
  (ev-extract string? params))

(defn- ev-get-opts [params]
  (ev-extract map? params))

(defn build-event-def
  "Builds the structure used internally for event management."
  [event-key event-params]
  (let [[docstring event-params] (ev-get-docstring event-params)
        [opts event-params] (ev-get-opts event-params)]
    {:key event-key :docstring docstring :options opts :params event-params}))

(defn register-event
  "Adds an event structure to the registered-events list"
  [event-key & params]
  ;; take a vector with the following items, in order:
  ;;    keyword -- the event name
  ;;    string [optional] -- event docstring
  ;;    map [optional] -- event options
  ;;    & keywords -- used to build the map that will be passed to
  ;;    event handlers.
  (let [event-struct (build-event-def event-key params)
        key (:key event-struct)]
    (if-let [existing (key @registered-events)]
      (throw (Exception. (str "Duplicate event definition: " key (:docstring existing)))))
    (swap! registered-events assoc key event-struct))
  )

(defn register-handler
  "Registers a handler for the given event key. The event key can be a single keyword, or
a set of keywords, to match any of the contained keys, or a regex pattern, to match any key
whose name matches the regex. For any given key, the handlers will be called in the order
they were defined. Handler functions take two args. The first will be a response map, containing
the response of the previous handler, initially {}. The second will be a vector of any additional
arguments given when the event is triggered"
  [event-key handler-fn]
  (swap! registered-handlers event-key (conj
                                        (or (event-key registered-handlers)
                                            [])
                                        handler-fn)))

(defn remove-handler
  "Removes the given handler from the given event key(s). The event key can be a
single keyword, or a set of keywords, to match any of the contained keys, or a regex pattern,
to match any key whose name matches the regex."
  [event-map event-key handler-fn]
  (r/remove-handler event-map event-key handler-fn))

(defn trigger
  "Triggers the event corresponding to the given key, which must be a single keyword.
Any additional arguments will be passed to each of the registered handlers. The trigger
function returns immediately; event processing is handled in a different thread."
  [event-map event-key & args]
  ;; TODO implement this method
  nil
  )

(defn poll
  "Triggers the event corresponding to the given key, which must be a single keyword.
Any additional arguments will be passed to each of the registered handlers. The poll
function does not return until all handlers have been called on the function, and returns
the result of the last handler that was called."
  [event-key & args]
  ;; TODO implement this method
  nil
  )

(defn build
  "Compiles the event dictionary for faster processing. Does nothing if the
dictionary has already been compiled."
  []
  ;; TODO implement this method
  nil
  )

(defn doc
  "Returns the doc string that was provided when the event was declared."  
  [event-key]
  (if-let [event (event-key registered-events)]
    (let [docstring (:docstring event)
          line1 (apply str event-key (:params event))]
      (str line1 "\n" docstring))
    (str "Unknown event " event-key)))


