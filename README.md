# Barnum Events

The Barnum event engine provides a generic library for defining arbitrary
events, assigning handlers to those events, and firing events. Event firing
is asynchronous, but returns a future, so you can have event handlers that
return a result that you process whenever you like.

[![Build Status](https://travis-ci.org/manutter51/barnum.png)](https://travis-ci.org/manutter51/barnum.png)

## Installation

To use the Barnum event engine in your project, add the following to the
dependencies in your `project.clj` file:

    ["barnum" "0.1.0"]

## Usage

    (ns my.namespace
      (:require [barnum.events :as ev]))

The core functionality of Barnum can be broken down into three types of 
tasks.

 * Defining events
 * Registering event handlers
 * Firing events

In addition, Barnum has functions for checking the number of handlers 
assigned to a given event, and for re-arranging the order in which the 
handlers are executed in response to an event. You can also attach
validation functions to an event to ensure that the correct arguments
are passed to the event handlers when the event is fired.

### Defining events

Events are defined using keywords as the event name. For simple systems,
you can use simple keywords like `:open` or `:init`, but for more complex
systems, you might consider using hyphenated names like `:resource-load`
and `:resource-start`. To add an event definition, use the `add-event`
function

    (ev/add-event event-key docstring options params)

The docstring, options, and params arguments are optional. The docstring
argument describes the purpose of the event, and can be retrieved at the
REPL using `(ev/docs :event-key)`.

The options argument is a map with the following keys:

 * `:min-handlers` - minimum number of handlers required by this event
 * `:max-handlers` - maximum number of handlers required by this event
 * `:defaults` - maps params to their default value, if not nil
 * `:validation-fn` - function to call when event fires, to validate params

In an architecture where you call one or more plugins to set up your event
handlers, you can call `(ev/check)` after setup to compare the number
of handlers assigned to each event against the `:min-handlers` (default
zero) and `:max-handlers` (default MAXINT) for that event. When the event
fires, default values will be assigned to each event parameter according
to the `:defaults` map (or to nil, for any params not in the `:defaults`
map), and then the `:validation-fn` function, if any, will be called to make
sure the params and param values are correct.

The params argument lists the parameters that can/should/must be given
as parameters for the event when the event fires. Each event handler takes
this parameter map as its argument, and the map's keys should correspond to
the params given when the event is defined. For convenience, the params
can be specified either as a vector of keywords, a symbol containing a
vector of keywords, or as in-line keywords.

Example: Define an event named `:my-event` which should have at most one
handler and which takes the params `:param-1` and `:param-2`.

    (add-event :my-event 
               "Docstring about my event"
               {:min-handlers 0, :max-handlers 1} 
               :param-1 :param-2)

*Note:* Each event can be defined only once. Attempting to add the same
event key more than once will throw an exception.

Use the `(ev/event-keys)` function to get a list of all currently defined
event keys.

### Adding Event Handlers

    (ev/add-handler :my-event :my-handler my-handler-fn)

To register an event handler, call `add-handler` with the event key, a
unique handler key, and the handler function. Each handler function must
take a single argument, which is the map containing the event parameters,
as defined by the params argument to `add-event`. In addition to the
parameters defined by `add-event`, the parameter map will also contain
three additional parameters:

 * `:_event` - The event key (in case the same handler handles more than one event)
 * `:_handler` - The handler key of the current handler, for logging, error-handling, etc
 * `:_fired-at` - A Java Date object recording the time the event fired

You can use `(ev/remove-handler event-key handler-key)` to remove a handler,
or `(ev/replace-handler event-key handler-key new-fn)` to replace an
existing handler. To add a handler and overwrite any existing handler that
might be using the same key, use `(ev/add-or-replace-handler event-key handler-key handler-fn)`.

You cannot register the same handler key more than once for the same event 
but you could, if needed, register the same function under more than one 
handler key.

If you want to register the same handler for multiple events, pass a set of
event keys as the first argument to `add-handler`. For example, to log all
events, you might do something like the following:

    (ev/add-handler
      (into #{} (ev/event-keys))
      :logging
      my-log-fn)

When each event is fired, the handlers registered for that event will be
called in the order they were added. Each handler function should return
a "beanbag" (see below) indicating success or failure, and whether or not
subsequent handlers should be called.

### Writing event handlers

[Beanbag][1] is a very small library for wrapping function call results 
inside a tuple along with a status key. Barnum uses beanbags to return both
data as calculated by the handler, and also a status key indicating whether
the handler completed successfully, encountered an error, or skipped any
processing of the event for benign reasons. The status key can also 
indicate whether or not subsequent event handlers should be called.

[1]: https://github.com/manutter51/beanbag

To write an event handler, `require` the `beanbag.core` namespace, and then
use the `ok`, `skip`, and `fail` functions to wrap any data returned by your
handler.

    (ns my.handler-ns
      (:require [beanbag.core :refer [ok skip fail]]))

    (defn my-handler [args]
      (let [data (:data args)]
        (if data
          (ok (str "Data is " data))
          (fail "Data was nil"))))

Barnum checks for a few different status keys besides the generic
`:ok`, `:skip`, and `:fail` keys provided by Beanbag. You can return these
status keys by passing them in as the optional first argument to `ok`, 
`skip`, or `fail`.

    (defn my-handler [args]
      (let [data (:data args)]
        (cond
          (even? data) (ok :ok-stop "Data was even, don't run any other handlers")
          (odd? data) (fail :abort "Data was odd, don't run any other handlers")
          (skip "Didn't handle the data for some reason"))))

The status keys you can use are as follows:

 * `:ok` - report success, return data, and continue with next handler
 * `:ok-stop` - report success, return data, but do not execute any remaining handlers
 * `:fail` - report error, return error info, and continue with the next handler
 * `:abort` - report error, return error info, do not process any further handlers
 * `:skip` - report event not handled, return status info (if any), and continue with the next handler.

The `:ok`, `:skip` and `:fail` keys do not need to be explictly specified,
since they are the default status keys for the `ok`, `skip`, and `fail`
functions, but `:ok-stop` and `:abort` must be given as the first argument
in order to prevent subsequent handlers from being called.

Inside a handler function, you can do anything you like, including firing
off other events. 

### Managing event handlers

Use the `check` function to make sure the handlers you have assigned to
your events match the constraints on the events. The `check` function
returns a map of event keys mapped to a list of error messages for that
event, or an empty map if there are no errors.

    (if-let [event-errors (ev/check)]
      (report-errors event-errors)
      (proceed-with-application))

For any given event, you can control the order in which the handlers
are executed by using the `order-first` and `order-last` functions. The
`order-first` function takes an event key and a list of handler keys, and
re-orders the handlers to match the order you specify. Any handler keys
that are not in the list will be executed after the keys you did specify,
in their original order.

The `order-last` function works the same way, except that any keys you do
not specify will be executed *before* the keys you do specify.

    ;; Example: Event :e1 has handlers :h1 :h2 :h3 :h4 and :h5
    (ev/order-first :e1 [:h4 :h2]) ; ==> :h4 :h2 :h1 :h3 :h5

    ;; Example: Event :e1 has handlers :h1 :h2 :h3 :h4 and :h5
    (ev/order-last :e1 [:h4 :h2]) ; ==> :h1 :h3 :h5 :h4 :h2

Use the `handler-keys` function to get a list of the current handlers,
in order, for any given event.

    (let [h-keys (ev/handler-keys :event-1)]
      (do-something-with-keys h-keys))

### Triggering events

To trigger an event and execute its associated handlers, use the `fire`
function. The `fire` function takes the event key as its first argument,
followed by zero or more key/value pairs.

    (ev/fire :some-event :data-1 "Some data", :data-2 "More data")

The `fire` function returns the collected results of all the handlers 
for that event, in last-to-first order. To get the result of the last 
handler to fire, just call `first` on the seq. By default, events are
handled synchronously, but you can easily trigger asynchronous event
handling by wrapping `fire` inside a `future`.

### Validating event parameters

You can write custom validation functions to be used whenever an event
is fired, to check the parameters being passed along with the event. This
validation function should take two arguments: `params` and `args`. The
`params` argument is the list of parameters specified when the event was
defined, and the `args` argument is a map of key-value pairs where the key
is one of the params, and the value is the data associated with that key
at the time the event was fired. Return a list of validation errors, if 
any, or nil if there were no errors.

To assign a validation function to an event, provide the function as the
`:validation-fn` key in the options map when you initially add the event.

    (require '[barnum.events.validation :refer [require-all]])
    (add-event :e1 "My Event" {:validation-fn require-all} [:arg1 :arg2])

Barnum includes the following predefined validation functions, in the
`barnum.events.validation` namespace:

 * `require-all` -- fails if any args from the params list have values that are nil
 * `restrict-all` -- fails if any key in the `args` map is not in the `params` list

## License

Copyright © 2013 Mark Nutter

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
