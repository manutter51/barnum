(ns barnum.events
  (:refer-clojure :exclude [declare compile])
  (:require [barnum.events.register :as r]))

(defn
  ^{:doc "Defines an event dictionary. Pass in a map of event keys and doc strings. It is an error
to try and register a handler for a key that has not been declared."}
  declare [m]
  (-> {}
      (with-meta {:event-keys (set (keys m)) :help m :compiled false})
      (into (for [k (keys m)] [k []]))))

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

(def ^:dynamic *event-map* (atom nil))

(defn
  ^{:doc "Registers a handler for the given event or set of events."}
  register-handler [event-key handler-fn]
  (swap! *event-map* register-handler* event-key handler-fn)
  handler-fn)

(defn ^{:doc "Triggers an event asynchronously."}
  trigger [event-key & args]
  (apply trigger* @*event-map* event-key args))

(defn ^{:doc "Returns the result of polling all the event handlers for the given event key"}
  poll [event-key & args]
  (apply poll* @*event-map* event-key args))

(defmacro with-events [event-map & body]
  `(binding [*event-map* (atom (barnum.events/declare ~event-map))]
     ~@body))


