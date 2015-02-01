(ns barnum.events
  (:require [clojure.set :as set]
            [beanbag.core :refer [ok skip fail beanbag? cond-result]]))

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
        (keyword? event-key)
        (if (nil? (event-key @registered-events))
          (throw (Exception. (str "Cannot register handler "
                                  handler-key " for unknown event "
                                  event-key)))
          (dosync (let [handlers (or (event-key @registered-handlers) [])
                        existing (filter #(= handler-key (first %)) handlers)
                        handlers (conj handlers [handler-key handler-fn])]
                    (if (empty? existing)
                      (commute registered-handlers assoc event-key handlers )
                      (throw (Exception. (str "Duplicate event handler "
                                              handler-key " for event "
                                              event-key)))))
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

(defn validate-params
  "Check for a validation function and call it on the args, if present."
  [event args]
  (let [options (:options event)
        validation-fn (:validation-fn options)
        params (:params event)]
    (when (fn? validation-fn)
      (validation-fn params args))))

(defn run*
  "Call each handler in turn, accumulating the results of each handler
called, and returning a seq of beanbag results, in reverse order of
execution (i.e. the first item will be the result of the last handler
called)."
  ([handlers args]
     (run* handlers args '()))
  ([handlers args results]
     (let [handler (first handlers)
           [handler-key handler-fn] handler
           handlers (rest handlers)
           run-args (assoc args
                      :_handler handler-key)]
       (if (nil? handler-fn)
         results
           (cond-result
            result (handler-fn run-args)
            :ok (recur handlers args (conj results (ok result)))
            :ok-stop (conj results (ok result))
            :fail (recur handlers args (conj results (fail result)))
            :abort (conj results (fail result))
            :skip (recur handlers args (conj results (skip result)))
            (recur handlers args (conj results
                                       (ok :ok-unknown result))))))))

(defn fire
  "Triggers the event corresponding to the given key, which must be a
single keyword. Any additional arguments will be passed to each of the
registered handlers. Returns the accumulated results of calling each
of the handlers in turn."
  [event-key args]
  (let [event (or (event-key @registered-events)
                  (throw (Exception. (str "Event not defined: " event-key))))
        handlers (event-key @registered-handlers)
        num-handlers (count handlers)]
    (if (zero? num-handlers)
      (skip (str "No handlers for event " event-key))
      (let [defaults (or (:defaults (:options event)) {})
            args (merge defaults args)
            validation-errors (validate-params event args)
            ok? (empty? validation-errors)
            args (when ok? )
            args (assoc args
                   :_event event-key
                   :_fired-at (java.util.Date.))]
        (if ok?
            (run* handlers args)
            (fail validation-errors))))))

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


