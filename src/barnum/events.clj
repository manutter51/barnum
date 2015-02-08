(ns barnum.events
  (:require [clojure.set :as set]
            [barnum.results :as res]))

(def registered-events (atom {}))
(def registered-handlers (ref {}))
#_(def tmp-handlers (atom {}))

(defn- get-docstring [params]
  (let [docstring (first params)
        docstring (if (string? docstring) docstring)
        params (if docstring
                 (next params)
                 params)]
    [docstring params]))

(defn- parse-options* [& [k v & more]]
  (if (nil? k)
    {}
    (if (seq more)
      (assoc (apply parse-options* more) k v)
      {k v})))

;; Get event options. Possible options are:
;;  * min-handlers -- minimum number of handlers required
;;  * max-handlers -- maximum number of handlers allowed

(defn- parse-options [args]
  (let [opt (apply parse-options* args)
        valid-keys #{:min-handlers :max-handlers}
        min-handlers (:min-handlers opt 0)
        max-handlers (:max-handlers opt Integer/MAX_VALUE)]
    (if-let [wrong (seq (filter (complement valid-keys) (keys opt)))]
      (throw (Exception. (str "Not a valid event option: " (pr-str wrong)))))
    (if-not (number? min-handlers)
      (throw (Exception. "Invalid value for :min-handlers")))
    (if-not (number? max-handlers)
      (throw (Exception. "Invalid value for :max-handlers")))
    (if-not (<= min-handlers max-handlers)
      (throw (Exception. ":min-handlers must be less than or equal to :max-handlers")))
    {:min-handlers min-handlers
     :max-handlers max-handlers}))

(defn build-event-def
  "Builds the structure used internally for event management."
  [event-key more]
  (let [[docstring more] (get-docstring more)
        opts (parse-options more)]
    {:key event-key
     :docstring docstring
     :options opts}))

(defn register-event
  "Adds an event structure to the registered-events list"
  [event-key params]
  ;; takes the event name plus a vector with the following items, in order:
  ;;    string [optional] -- event docstring
  ;;    event options -- :min-handlers or max-handlers, followed by a number
  (if-not (keyword? event-key)
    (throw (Exception. "Event key must be a keyword")))
  (let [event-struct (build-event-def event-key params)]
    (if-let [existing (event-key @registered-events)]
      (throw (Exception. (str
                          "Duplicate event definition: " event-key " "
                          (:docstring existing)))))
    (swap! registered-events assoc event-key event-struct)))

(declare register-handlers)
(defn register-handler
  [event-key handler-key handler-fn]
  (cond (set? event-key) (register-handlers event-key handler-key handler-fn)
        (keyword? event-key) (if (nil? (event-key @registered-events))
                               (throw (Exception. (str "Cannot register handler "
                                  handler-key " for unknown event " event-key)))
                               (dosync (let [handlers (or (event-key @registered-handlers) [])
                                             existing (filter #(= handler-key (first %)) handlers)
                                             handlers (conj handlers [handler-key handler-fn])]
                                         (if (empty? existing)
                                           (commute registered-handlers assoc event-key handlers )
                                           (throw (Exception. (str "Duplicate event handler "
                                                                   handler-key " for event "
                                                                   event-key)))))
                                       ;; return handlers, for debugging/repl
                                       @registered-handlers))
        :else (throw (Exception.
                      "Event key must be a keyword or a set of keywords."))))

(defn register-handlers [event-keys handler-key handler-fn]
  (doseq [event-key event-keys]
    (register-handler event-key handler-key handler-fn)))

(defn handler-keys [event-key]
  (let [handlers (event-key @registered-handlers)]
    (map first handlers)))

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
     (let [[found remaining-handlers] (ev-extract
                                       #(= current-key (first %))
                                       handlers)
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

(declare remove-handlers)
(defn remove-handler
  "Removes the given handler from the given event key(s). The event
key can be a single keyword, or a set of keywords, to match any of
the contained keys."
  [event-key handler-key]
  (cond (set? event-key) (remove-handlers event-key handler-key)
        (keyword? event-key)
        (if (nil? (event-key @registered-events))
          (throw (Exception. (str "Cannot remove handler " handler-key
                                  " for unknown event " event-key)))
          (dosync (let [handlers (or (event-key @registered-handlers) [])
                        handlers (filter
                                  #(not= handler-key (first %))
                                  handlers)]
                    (commute registered-handlers
                             assoc event-key handlers ))
                  @registered-handlers))
        :else (throw (Exception.
                      "Event key must be a keyword or a set of keywords."))))

(defn remove-handlers
  [event-keys handler-fn]
  (doseq [event-key event-keys]
    (remove-handler event-key handler-fn)))

(defn- replacer
  [handler-key handler-fn]
  (fn [[current-key current-fn]]
    (if (= current-key handler-key)
      [current-key handler-fn]
      [current-key current-fn])))

(declare replace-handlers)
(defn replace-handler
  "Replaces the matching handler (if any) with the new function. If the function has not been set as a handler for the given event(s), then no replacement will take place."
  [event-key handler-key handler-fn]
  (cond (set? event-key) (replace-handlers event-key handler-key handler-fn)
        (keyword? event-key)
        (dosync
         (let [handlers (event-key @registered-handlers)
               handlers (doall (map (replacer handler-key handler-fn)
                                    handlers))]
           (commute registered-handlers
                    assoc event-key handlers)))
        :else (throw (Exception.
                      "Event key must be a keyword or a set of keywords."))))

(defn replace-handlers
  [event-keys handler-key handler-fn]
  (doseq [event-key event-keys]
    (replace-handler event-key handler-key handler-fn)))

(defn check
  "Checks the current event map and ensures that each event has at least
the minimum number of handlers specified in the min-handlers option, but
no more than the maximum number of handlers specified in the max-handlers
option. Returns a map of event keys mapped to error messages for any events
that have errors."
  []
  (into {}
        (let [all-events @registered-events
              all-handlers @registered-handlers]
          (for [event-key (keys all-events)]
            (let [event (event-key all-events)
                  options (or (:options event) {})
                  min-handlers (or (:min-handlers options) 0)
                  max-handlers (or (:max-handlers options) Integer/MAX_VALUE)
                  handlers (event-key all-handlers)
                  num-handlers (count handlers)
                  errors []
                  errors (if (< num-handlers min-handlers)
                           (conj errors (str "The " event-key
                                             " event needs at least "
                                             min-handlers " handler(s), has "
                                             num-handlers))
                           errors)
                  errors (if (> num-handlers max-handlers)
                           (conj errors (str "The " event-key
                                             " event can have at most "
                                             max-handlers " handler(s), has "
                                             num-handlers))
                           errors)]
              (if (empty? errors)
                nil
                [event-key errors]))))))

(defn- set-1-validation-fn! [event-key validator-fn override?]
  (let [existing (get-in @registered-events [event-key :validation-fn])]
    (if (and existing (not= :override override?))
      (throw (Exception.
               (str "Cannot replace validation function on event "
                    event-key
                    ", override flag not specified."))))
    (swap! registered-events assoc-in [event-key :validation-fn] validator-fn)))

(defn set-validation-fn!
  "Sets the validation fn for a specific event or set of events. Pass nil as the
validator function to disable validation."
  [event-key validator-fn & [override?]]
  (let [event-keys (if (set? event-key) event-key #{ event-key })]
    (doseq [k event-keys]
            (if-not (keyword? k) (throw (Exception. (str "Cannot set validation function: "
                                                         (pr-str k) " is not a keyword"))))
            (set-1-validation-fn! k validator-fn override?))))

(defn validate-args
  "Check for a validation function and call it on the args, if present."
  [event-key ctx args]
  (let [event (event-key @registered-events)
        validation-fn (:validation-fn event)]
    (if (fn? validation-fn)
      (validation-fn ctx args)
      (res/ok args))))

(defn- run*
  "Call each handler in turn, passing the given args to the first handler, and
passing each successive handler the results returned by the previous handler. If
a handler returns anything other than an \"ok\" result, stop processing handlers,
and either fire the next event (on ok-go or fail-go), or return the error result
(on fail). Args must pass validation as defined by validation-fn for this event,
(if any)."
  [handlers ctx args]
  (let [handler (first handlers)
        [handler-key handler-fn] handler
        handlers (rest handlers)
        event-key (::event-key ctx ::no-event)
        log (::log ctx [])
        log (conj log [(System/currentTimeMillis) event-key handler-key])
        ctx (assoc ctx ::handler-key handler-key ::log log)]
    (if (nil? handler-fn)
      (assoc (res/ok args) ::context ctx)
      (let [validation-result (validate-args event-key ctx args)
            validation-status (:status validation-result)
            errors (:errors validation-result)
            data (:data validation-result)]
        (if-not (= :ok validation-status)
          (assoc (res/not-valid errors data) ::context ctx)
          (let [handler-result (handler-fn ctx data)
                status (:status handler-result)
                data (:data handler-result)
                next-event-key (:next handler-result)
                error-key (:error-key handler-result)
                message (:message handler-result)
                error-event-key (:error-event-key handler-result)]
            (condp = status
              :ok (recur handlers ctx data)
              :ok-go (let [handlers (next-event-key @registered-handlers)
                           ctx (assoc ctx :event-key next-event-key)]
                       (recur handlers ctx data))
              :fail (assoc (res/fail error-key message data) ::context ctx)
              :fail-go (let [handlers (error-event-key @registered-handlers)
                             old-errors (::errors ctx [])
                             errors (conj old-errors [(::event-key ctx) (::handler-key ctx) error-key message])
                             ctx (assoc ctx ::errors errors ::event-key error-event-key)]
                         (recur handlers ctx data))
              ; else
              (throw (Exception. (str "Invalid event-handler result: " (pr-str handler-result)))))))))))

(defn fire
  "Triggers the event corresponding to the given key, which must be a
single keyword. The context (ctx) is a map intended for use by the application
to store any application-specific data (e.g. database connections and other
dependencies), as well as any metadata related to event system execution.
The args param is a map of key-value pairs that will be passed to each of the
registered handlers. Returns the accumulated results of calling each of the
handlers in turn."
  [event-key ctx args]
  (let [_ (or (event-key @registered-events)
              (throw (Exception. (str "Event not defined: " event-key))))
        handlers (event-key @registered-handlers)
        num-handlers (count handlers)
        ctx (assoc (or ctx {}) ::event-key event-key)]
    (if (zero? num-handlers)
      (assoc (res/ok args) ::context ctx)
      (run* handlers ctx args))))

(defn docs
  "Returns the doc string that was provided when the event was declared."  
  [event-key]
  (if-let [event (event-key @registered-events)]
    (let [docstring (:docstring event)
          line1 (apply "Params: " str event-key
                       "{" (doall
                        (interpose " _, " (:params event))) "}")]
      (str line1 "\n" docstring))
    (str "Unknown event " event-key)))


