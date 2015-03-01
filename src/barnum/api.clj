(ns barnum.api
  (:require [barnum.events :as ev]
            [barnum.results :as res]))

;; Wrappers and docstrings for all the functions that should be used by
;; apps/clients that require Barnum functions.

(defn add-event
  "Define an event to be handled by the system. The event is named by
the (unique) event-key and described by the optional docstring. Each
event can be further defined by the following options:
    :min-handlers The minimum number of handlers for this event
    :max-handlers The maximum number of handlers for this event

The remaining parameters to def-event specify the keys that should be
present in the map that gets passed to registered event handlers when
the event is fired or polled. Throws an exception if you try to declare the
same event more than once."
  [ctx event-key & params]
  (ev/register-event ctx event-key (vec params)))

(defn event-keys
  "Returns a list of all currently defined event keys."
  [ctx]
  (keys (:barnum.events/registered-events ctx [])))

(defn handler-keys
  "Returns a list of all currently defined handler keys for the given event.
Throws an exception if the specified event does not exist."
  [ctx event-key]
  (ev/handler-keys ctx event-key))

(defn add-handler
  "Registers a handler for the given event key(s). The event key can be a
single keyword, or a set of keywords, to match any of the contained keys.
For any given key, the handlers will be called in the order they were
defined unless you explicitly set the handler order using order-first or
order-last. Handler functions take a single arg: a map containing the keys
given when the event was defined, and their values. Throws an exception if
you try to declare the same handler key more than once for the same
event."
  [ctx event-key handler-key handler-fn]
  (ev/register-handler ctx event-key handler-key handler-fn))

(defn remove-handler
  "Removes a given handler from a given event or set of events. If
the given handler is not assigned to the given event, the call
succeeds silently and does not change the list of registered handlers."
  [ctx event-key handler-key]
  (ev/remove-handler ctx event-key handler-key))

(defn replace-handler
  "If the handler key exists for the given event, replaces that handler
with the given handler function. If the handler key has not been assigned
to that event, the call silently succeeds without changing the list of
registered handlers"
  [ctx event-key handler-key handler-fn]
  (ev/replace-handler ctx event-key handler-key handler-fn))

(defn add-or-replace-handler
  "Adds the given handler to the given event, replacing any previous handler
with the same handler key."
  [ctx event-key handler-key handler-fn]
  (ev/remove-handler ctx event-key handler-key)
  (ev/register-handler ctx event-key handler-key handler-fn))

(defn order-first
  "Re-orders the handlers for the given event so that they occur in the
same order as in the given vector of handler keys. Any handlers not listed
in the given vector will be appended to the end of the list in their original
order."
  [ctx event-key handler-key-list]
  (ev/order-first ctx event-key handler-key-list))

(defn order-last
  "Re-orders the handlers for the given event so that they occur in the
same order as in the given vector of handler keys. Any handlers not listed
in the given vector will be prepended to the beginning of the list in their
original order."
  [ctx event-key handler-key-list]
  (ev/order-last ctx event-key handler-key-list))

(defn check
  "Checks the current event map and ensures that each event has at least
the minimum number of handlers specified in the min-handlers option, but
no more than the maximum number of handlers specified in the max-handlers
option. Returns a map of event keys mapped to error messages, for any events
that have errors."
  [ctx]
  (ev/check ctx))

(defn set-validation-fn
  "Sets the validation function to be used to validate the event data being
passed to an event handler. Your validation function should take 2 arguments,
`ctx` (a map containing application-specific context) and data (the values to
be processed by the handler). Return nil if there are no errors in the data,
or a map of keys and error messages, where the key identifies which data
value had the error. For example, if the data was supposed to contain a key
for :email, and the key was missing or the corresponding value was nil,
your result would be {:email \"Required email not found\"}.

Only one validation function can be set on any given event. To replace an
existing validation function, pass :override as the third argument to this
function.

To attach the same validation function to multiple events, pass a set of event
keys as the first argument."
  [ctx event-key validation-fn & [override?]]
  (ev/set-validation-fn ctx event-key validation-fn override?))

(defn fire
  "Attempts to fire the given event using the given args (specified as a map of
key-value pairs), after first supplying any default values and then
validating the args with the validation function supplied when the event
was added (if any).  Returns a map containing the status of the last event handler
to fire plus the data returned by the handler. If no handler is defined for the
given event, returns a status of :ok, plus the unmodified original data. Throws an
Exception if the event has not been defined with add-event.

Takes an optional Barnum context (hash-map) as the second argument. The Barnum
event engine uses this internally to compile a history of events and/or errors
that occur during event handling."
  [ctx event-key & args]
  (let [args (apply hash-map args)]
    (ev/fire ctx event-key args)))

(defn fire-all
  "Triggers a series of events, such that each successive event receives
the data returned by the previous event. If any event in the series returns
a fail result, fire-all returns the failed result immediately, without firing
any of the subsequent events."
  [ctx event-keys & args]
  (let [args (apply hash-map args)]
    (ev/fire-all ctx event-keys args)))

(defn docs
  "Returns the docstring supplied when the event was added, plus a list
of params and defaults to be supplied when the event is fired."
  [ctx event-key]
  (ev/docs ctx event-key))

(defn ok
  "Returns a correctly-formatted tuple containing the handler status (ok)
and the processed data. Used by event handlers to return results to the
Barnum event engine. Data will be passed to the next handler for this
event, if any, or returned to the function that fired the original event
if there are no more handlers to call."
  [data]
  (res/ok data))

(defn ok-go
  "Returns a correctly-formatted tuple containing the handler status (ok),
the event key for the next event to be fired, and the processed data. Used
by event handlers to return results to the Barnum event engine and trigger
a follow-up event without consuming stack space that might lead to a stack
overflow. Any additional handlers for the current event will be skipped."
  [next-event-key data]
  (res/ok-go next-event-key data))

(defn fail
  "Returns a correctly-formatted tuple containing the handler status (fail)
and the processed data. Used by event handlers to return results to the
Barnum event engine. Any additional handlers for the current event will be
skipped, and the data will be returned immediately to the function that
fired the original event."
  [error-key error-message data]
  (res/fail error-key error-message data))

(defn fail-go
  "Returns a correctly-formatted tuple containing the handler status (ok),
the event key for the next event to be fired, and the processed data. Used
by event handlers to return results to the Barnum event engine and trigger
a follow-up event without consuming stack space that might lead to a stack
overflow. Any additional handlers for the current event will be skipped."
  [error-event-key error-key error-message data]
  (res/fail-go error-event-key error-key error-message data))

(defn not-valid
  "Returns a correctly-formatted tuple containing the validation status (not
valid), a map of keys and error messages (where the key in the error map matches
the key of the data value that failed validation), and a copy of the data that
was validated. Used by validation functions to return error messages when validation
checks fail."
  [errors data]
  (res/not-valid errors data))