(ns barnum.results
  (require [beanbag.core :as bb]))

(defn ok [data]
  (bb/ok data))

(defn ok-go [next-event-key data]
  (when-not (keyword? next-event-key)
    (throw (Exception. (str "Expected event key for next event to fire; got " (class next-event-key)))))
  (bb/ok (assoc data ::next-event next-event-key)))

;; TODO Need some way to instrument this with the event key and handler key where it takes place...
(defn fail [error-message data]
  (let [existing-errors (:barnum.errors/errors data [])
        data (assoc data :barnum.errors/errors (conj existing-errors error-message))]
    (bb/fail data)))

;; TODO update this to match fail fn
(defn fail-go [error-event-key error-message data]
  (when-not (keyword? error-event-key)
    (throw (Exception. (str "Expected event key for error event to fire; got " (class error-event-key)))))
  (let [existing-errors (:barnum.errors/errors data [])
        data (assoc data :barnum.errors/errors (conj existing-errors error-message))]
    (bb/fail (assoc data ::error-event error-event-key :barnum.errors/errors (conj existing-errors error-message)))))

