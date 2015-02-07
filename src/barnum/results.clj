(ns barnum.results)

(defn ok [data]
  [:ok data])

(defn ok-go [next-event-key data]
  (when-not (keyword? next-event-key)
    (throw (Exception. (str "Expected event key for next event to fire; got " (class next-event-key)))))
  [:ok-go next-event-key data])

(defn fail [error-message data]
  [:fail error-message data])

(defn fail-go [error-event-key error-message data]
  (when-not (keyword? error-event-key)
    (throw (Exception. (str "Expected event key for error event to fire; got " (class error-event-key)))))
  [:fail-go error-event-key error-message data])

(defn not-valid [errors data]
  [:not-valid errors data])