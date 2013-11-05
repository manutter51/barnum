# Barnum Events

The Barnum event engine provides a generic library for defining arbitrary
events, assigning handlers to those events, and firing events. Event firing
is asynchronous, but returns a future, so you can have event handlers that
return a result that you process synchronously.

[![Build Status](https://travis-ci.org/manutter51/barnum.png)](https://travis-ci.org/manutter51/barnum.png)

## Installation

To use the Barnum event engine in your project, add the following to the
`dependencies` in your `project.clj` file:

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
a parameter map as its argument, and the map's keys should correspond to
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
existing handler. You cannot register the same handler key more than once 
for the same event but you could, if needed, register the same function
under more than one handler key.

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
data as calculated by the handler, and also metadata indicating whether
the handler completed successfully, encountered an error, or skipped any
processing of the event for benign reasons.

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

Barnum actually checks for a few different status keys besides the generic
`ok`, `skip`, and `fail` keys provided by Beanbag. You can return these
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

### Triggering events

To trigger an event and execute its associated handlers, use the `fire`
function. The `fire` function takes the event key as its first argument,
followed by zero or more key/value pairs.

    (ev/fire :some-event :data-1 "Some data", :data-2 "More data")

The `fire` function returns a future representing the collected results
returned by the handlers for that event, in last-to-first order. To get
the result of the last handler to fire, just call `first` on the seq.

## TODO

 * Add a mechanism for sharing data between handlers?
 * Document re-ordering event handlers.

## License

Copyright © 2013 Mark Nutter

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
