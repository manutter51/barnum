(ns barnum.api
  (:require [barnum.events :as ev]))

(defn def-event
  "Define an event to be handled by the system. The event is named by
the (unique) event-key and described by the optional docstring. Each
event can be further defined by the following options:
    :min-handlers The minimum number of handlers for this event
    :max-handlers The maximum number of handlers for this event
    :async If true, can only be fired; if false, can only be polled
If the async option is not present, the event can be either fired or
polled.

The remaining parameters to def-event specify the keys that will be
present in the map that gets passed to registered event handlers when
the event is fired or polled."
  [event-key & params]
  (apply ev/register-event event-key params))

(defn register-handler
  "Registers a handler for the given event key(s). The event key can be a single keyword, or
a set of keywords, to match any of the contained keys. For any given key, the handlers will
be called in the order they were defined unless you explicitly set the handler order using
order-handlers. Handler functions take two args. The first will be a map containing the keys
given when the event was defined, and their values. The second will be a response map,
containing the response of the previous handler, initially {}."
  [event-key handler-key handler-fn]
  (ev/register-handler event-key handler-key handler-fn))
