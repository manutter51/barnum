(ns barnum.events)

(def registered-events (atom {}))
(def registered-handlers (ref {}))
(def tmp-handlers (atom {}))

(defn- ev-get-docstring [params]
  (let [docstring (first params)
        docstring (if (string? docstring) docstring)
        params (if docstring
                 (next params)
                 params)]
    [docstring params]))

(defn- ev-get-opts [params]
  (let [opts (first params)
        opts (if (map? opts) opts)
        params (if opts
                 (next params)
                 params)
        params (if (seq params)
                 (vec params))]
    [opts params]))

(defn build-event-def
  "Builds the structure used internally for event management."
  [event-key event-params]
  (let [[docstring event-params] (ev-get-docstring event-params)
        [opts event-params] (ev-get-opts event-params)]
    (if-not (every? keyword? event-params)
      (throw (Exception. "Event params must all be keywords")))
    {:key event-key :docstring docstring :options opts :params event-params}))

(defn register-event
  "Adds an event structure to the registered-events list"
  [event-key & params]
  ;; takes the event name plus a vector with the following items, in order:
  ;;    string [optional] -- event docstring
  ;;    map [optional] -- event options
  ;;    & keywords -- used to build the map that will be passed to
  ;;    event handlers.
  ;; TODO Add option for cycle detection -- number of time event can
  ;; appear in backtrace before triggering a "Cycle detected" error
  (if-not (keyword? event-key)
    (throw (Exception. "Event key must be a keyword")))
  (let [event-struct (build-event-def event-key params)
        key (:key event-struct)]
    (if-let [existing (key @registered-events)]
      (throw (Exception. (str "Duplicate event definition: " key " " (:docstring existing)))))
    (swap! registered-events assoc key event-struct)))

(declare register-handlers)
(defn register-handler
  [event-key handler-key handler-fn]
  (cond (set? event-key) (register-handlers event-key handler-key handler-fn)
        (keyword? event-key)
        (if (nil? (event-key @registered-events))
          (throw (Exception. (str "Cannot register handler " handler-key " for unknown event " event-key)))
          (dosync (let [handlers (or (event-key @registered-handlers) [])
                        existing (filter #(= handler-key (first %)) handlers)
                        handlers (conj handlers [handler-key handler-fn])]
                    (if (empty? existing)
                      (commute registered-handlers assoc event-key handlers )
                      (throw (Exception. (str "Duplicate event handler " handler-key " for event " event-key)))))
                  @registered-handlers))
        :else (throw (Exception. "Event key must be a keyword or a set of keywords."))))

(defn register-handlers [event-keys handler-key handler-fn]
  (doseq [event-key event-keys]
    (register-handler event-key handler-key handler-fn)))

(defn add-extracted [extracted found]
  (vec (filter identity (conj extracted found))))

(defn- ev-extract [fn params]
  (let [classified (group-by fn params)
        tgt (first (classified true))
        params (classified false)]
    [tgt params]))

(defn extract-handlers
  ([key-list handlers]
     (extract-handlers (first key-list) (rest key-list) [] handlers))
  ([current-key key-list extracted handlers]
     (let [[found remaining-handlers] (ev-extract #(= current-key (first %)) handlers)
           extracted (add-extracted extracted found)
           next-key (first key-list)
           remaining-keys (rest key-list)]
       (if (nil? next-key)
         [extracted remaining-handlers]
         (recur next-key remaining-keys extracted remaining-handlers)))))

(defn order-first
  [event-key handler-key-list]
  (dosync
   (let [handlers (or (event-key @registered-handlers) [])
         [extracted leftover] (extract-handlers handler-key-list handlers)
         handlers (concat extracted leftover)]
     (when-not (empty? handlers)
       (commute registered-handlers assoc event-key handlers)))))

(defn order-last
  [event-key handler-key-list]
  (dosync
   (let [handlers (or (event-key @registered-handlers) [])
         [extracted leftover] (extract-handlers handler-key-list handlers)
         handlers (concat leftover extracted)]
     (when-not (empty? handlers)
       (commute registered-handlers assoc event-key handlers)))))

(defn remove-handler
  "Removes the given handler from the given event key(s). The event key can be a
single keyword, or a set of keywords, to match any of the contained keys, or a regex pattern,
to match any key whose name matches the regex."
  [event-map event-key handler-fn]
  nil)

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

(defn docstring
  "Returns the doc string that was provided when the event was declared."  
  [event-key]
  (if-let [event (event-key @registered-events)]
    (let [docstring (:docstring event)
          line1 (apply str event-key (:params event))]
      (str line1 "\n" docstring))
    (str "Unknown event " event-key)))


