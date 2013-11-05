(ns barnum.api
  (:require [barnum.events :as ev]))

(defn add-event
  "Define an event to be handled by the system. The event is named by
the (unique) event-key and described by the optional docstring. Each
event can be further defined by the following options:
    :min-handlers The minimum number of handlers for this event
    :max-handlers The maximum number of handlers for this event
    :validation-fn A function to call to validate the args when the event is fired
    :defaults A map of default values for any parameters. If you don't specify a default, the default will be nil.

The remaining parameters to def-event specify the keys that should be
present in the map that gets passed to registered event handlers when
the event is fired or polled. Throws an exception if you try to declare the
same event more than once."
  [event-key & params]
  (ev/register-event event-key (vec params)))

(defn event-keys
  "Returns a list of all currently defined event keys."
  []
  (keys @ev/registered-events))

(defn add-handler
  "Registers a handler for the given event key(s). The event key can be a
single keyword, or a set of keywords, to match any of the contained keys.
For any given key, the handlers will be called in the order they were
defined unless you explicitly set the handler order using order-first or
order-last. Handler functions take a single arg: a map containing the keys
given when the event was defined, and their values. Throws an exception if
you try to declare the same handler key more than once for the same
event."
  [event-key handler-key handler-fn]
  (ev/register-handler event-key handler-key handler-fn))

(defn remove-handler
  "Removes a given handler from a given event or set of events. If
the given handler is not assigned to the given event, the call
succeeds silently and does not change the list of registered handlers."
  [event-key handler-key]
  (ev/remove-handler event-key handler-key))

(defn replace-handler
  "If the handler key exists for the given event, replaces that handler
with the given handler function. If the handler key has not been assigned
to that event, the call silently succeeds without changing the list of
registered handlers"
  [event-key handler-key handler-fn]
  (ev/replace-handler event-key handler-key handler-fn))

(defn add-or-replace-handler
  "Adds the given handler to the given event, replacing any previous handler
with the same handler key."
  [event-key handler-key handler-fn]
  (ev/remove-handler event-key handler-key)
  (ev/register-handler event-key handler-key handler-fn))

(defn order-first
  "Re-orders the handlers for the given event so that they occur in the
same order as in the given vector of handler keys. Any handlers not listed
in the given vector will be appended to the end of the list in their original
order."
  [event-key handler-key-list]
  (ev/order-first event-key handler-key-list))

(defn order-last
  "Re-orders the handlers for the given event so that they occur in the
same order as in the given vector of handler keys. Any handlers not listed
in the given vector will be prepended to the beginning of the list in their
original order."
  [event-key handler-key-list]
  (ev/order-last event-key handler-key-list))

(defn check
  "Checks the current event map and ensures that each event has at least
the minimum number of handlers specified in the min-handlers option, but
no more than the maximum number of handlers specified in the max-handlers
option. Returns a map of event keys mapped to error messages, for any events
that have errors."
  []
  (ev/check))

(defn fire
  "Attempts to fire the given event using the given args (specified as
key-value pairs), after first supplying any default values and then
validating the args with the validation function supplied when the event
was added (if any).  Returns a future which can be dereferenced if
you need to get back a result, or simply ignored if you just want to fire
off an event asynchronously. Throws an Exception if the event has not been
defined with add-event."
  [event-key & args]
  (when-not (even? (count args))
    (throw (Exception. "All args after the event key must be specified as zero or more key-value pairs.")))
  (let [args-map (apply hash-map args)]
    (ev/fire event-key args-map)))

(defn docs
  "Returns the docstring supplied when the event was added, plus a list
of params and defaults to be supplied when the event is fired."
  [event-key]
  (ev/docs event-key))
