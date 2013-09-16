(ns barnum.events.register)

(defn- add-handler-to-key [event-map event-key handler-fn]
  (if-let [current (event-key event-map)]
    (assoc event-map event-key (conj current handler-fn))
    (throw (Exception. (str "Unknown event key " (name event-key))))))

(defn register-handler [event-map event-key handler-fn]
  nil)

(defn remove-handler [event-map event-key handler-fn]
  nil)
