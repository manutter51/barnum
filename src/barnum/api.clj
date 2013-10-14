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
