(ns barnum.events
  (:refer-clojure :exclude [declare compile])
  (:require [barnum.events.register :as r]))

(def registered-events (atom {}))

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

(defn
  ^{:doc "Registers a handler for the given event key. The event key can be a single keyword, or
a set of keywords, to match any of the contained keys, or a regex pattern, to match any key
whose name matches the regex. For any given key, the handlers will be called in the order
they were defined. Handler functions take two args. The first will be a response map, containing
the response of the previous handler, initially {}. The second will be a vector of any additional
arguments given when the event is triggered"}
  register-handler* [event-map event-key handler-fn]
  (r/register-handler event-map event-key handler-fn))

(defn ^{:doc "Removes the given handler from the given event key(s). The event key can be a
single keyword, or a set of keywords, to match any of the contained keys, or a regex pattern,
to match any key whose name matches the regex."}
  remove-handler* [event-map event-key handler-fn]
  (r/remove-handler event-map event-key handler-fn))

(defn
  ^{:doc "Triggers the event corresponding to the given key, which must be a single keyword.
Any additional arguments will be passed to each of the registered handlers. The trigger
function returns immediately; event processing is handled in a different thread."}
  trigger* [event-map event-key & args]
  ;; TODO implement this method
  nil
  )

(defn
  ^{:doc "Triggers the event corresponding to the given key, which must be a single keyword.
Any additional arguments will be passed to each of the registered handlers. The poll
function does not return until all handlers have been called on the function, and returns
the result of the last handler that was called."}
  poll* [event-map event-key & args]
  ;; TODO implement this method
  nil
  )

(defn
  ^{:doc "Compiles the event dictionary for faster processing. Does nothing if the
dictionary has already been compiled."}
  compile* [event-map]
  ;; TODO implement this method
  nil
  )

(defn
  ^{:doc "Returns the help string that was provided when the event was declared."
    }
  help* [event-map event-key]
  (let [help (:help (meta event-map))]
    (or (event-key help) (str "Unknown event key: " event-key))))

(defn
  ^{:doc "Registers a handler for the given event or set of events."}
  register-handler [event-key handler-fn]
  (comment (swap! registered-events register-handler* event-key handler-fn))
  handler-fn)

(defn ^{:doc "Triggers an event asynchronously."}
  trigger [event-key & args]
  (apply trigger* @registered-events event-key args))

(defn ^{:doc "Returns the result of polling all the event handlers for the given event key"}
  poll [event-key & args]
  (apply poll* @registered-events event-key args))

(defmacro with-events [event-map & body]
  `(binding [registered-events (atom (barnum.events/declare ~event-map))]
     ~@body))


