(ns barnum.results)

(defn ok [data]
  {:status :ok
   :data data})

(defn ok-go [next-event-key data]
  (when-not (keyword? next-event-key)
    (throw (Exception. (str "Expected event key for next event to fire; got " (class next-event-key)))))
  {:status :ok-go
   :next next-event-key
   :data data})

(defn ok-return [data]
  {:status :ok-return
   :data data})

(defn fail [error-key error-message data]
  {:status :fail
   :error-key error-key
   :message error-message
   :data data})

(defn fail-go [error-event-key error-key error-message data]
  (when-not (keyword? error-event-key)
    (throw (Exception. (str "Expected event key for error event to fire; got " (class error-event-key)))))
  {:status :fail-go
   :next error-event-key
   :error-key error-key
   :message error-message
   :data data})

(defn not-valid [errors data]
  {:status :not-valid
   :errors errors
   :data data})