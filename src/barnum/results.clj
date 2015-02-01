(ns barnum.results
  (require [beanbag.core :as bb]))

(defn ok [data]
  (bb/ok data))

(defn ok-go [next-event-key data]
  (when-not (keyword? next-event-key)
    (throw (Exception. (str "Expected event key for next event to fire; got " (class next-event-key)))))
  (bb/ok (assoc data ::next-event next-event-key)))

(defn fail [data]
  (bb/fail data))

(defn fail-go [error-event-key data]
  (when-not (keyword? error-event-key)
    (throw (Exception. (str "Expected event key for error event to fire; got " (class error-event-key)))))
  (bb/fail (assoc data ::error-event error-event-key)))

