# Barnum Events

The Barnum event engine provides a generic library for defining arbitrary
events, assigning handlers to those events, and firing events. Event firing
is asynchronous, but returns a future, so you can have event handlers that
return a result that you process whenever you like.

[![Build Status](https://travis-ci.org/manutter51/barnum.png)](https://travis-ci.org/manutter51/barnum.png)

## Installation

To use the Barnum event engine in your project, add the following to the
dependencies in your `project.clj` file:

    ["barnum" "0.2.0-SNAPSHOT"]

## Overview

Barnum is designed as a key component supporting an application architecture
that might be called Embedded Nanoservices. Embedded nanoservice architecture is
like microservice architecture, where key components of an application are
broken out into separate, self-contained services that interact with other services
via a published API. The downside of microservices, of course, is that this architecture
introduces additional latency in the form of inter-service network communications.

In nanoservice architecture this functionality is provided internally by handlers, or
small groups of handlers, connected through an event system (provided by Barnum) instead
of through network connections. Carefully designed, small groups of related event handlers
can provide the same sort of isolated, API-based services that microservices provide,
without the overhead of unnecessary network connections. And due to the abstraction
provided by the event-based architecture, the system can more easily be scaled when
needed by adding network connections and allowing events to propagate over the net.
This way an app can maximize efficiency when small, and scalability when large, with the
ability to fine-tune where the networking happens, to achieve the optimal trade-off between
latency and scalability.

Barnum's role in this architecture is to provide the mechanics for setting up and
executing the events and event handlers that define an application's functionality.
In the simplest use case, the application will register a particular event, say for
example `:app-init-read-config`, corresponding some processing that needs to occur,
such as reading its configuration file. The app will then register an appropriate
handler for this event, and probably a validation function to make sure the event
data includes the path to the configuration file. An optional timeout and timeout
handler function can also be set for the event.

Then, after all the events and event handlers have been configured, the application
will begin runtime execution by firing the appropriate events. As each event is triggered,
it will be given a map containing the event data. This map will be passed through
the validation function, if any, and after successfully validating, will be passed
to the handler function. The handler function will do whatever processing is necessary,
and then return a result. The result will always be a vector containing a status key
(`:ok` or `:ok-go`, for example), an optional relay event key, plus the result data. By
convention, an event handler should always return all the data that was given to it,
presumably modified by the handler, so that application state can be easily threaded
through a chain of event handlers.

The relay event key is used to specify the next event to be fired, to avoid the stack
overflow that might occur if one handler fired an event that triggered a handler that
fired an event that triggered a handler, etc. etc. By returning the relay event key
from the handler, trampoline-style, each handler can pass data on to the next stage
of processing without tying up a stack frame. If processing is complete and no further
events need to be triggered, simply omit the relay event key, and Barnum will return
the result to whatever process fired the original event.

### Multiple Event Handlers

Sometimes you may want multiple handlers to respond to the same event. For example,
you might want an event that saves a record in a database, makes a log entry, and
sends off an email notification, with the last two actions happening asynchronously
to the main program flow. Or you may have several handlers that need to look at the
data and decide whether to handle the event or hand it off to the next handler, depending
on (for example) the contents of a certain form field. Barnum allows you to handle
such cases by assigning multiple handlers to the same event.

To avoid confusion, each event handler must be given a unique ID in the form of a
keyword. These keyword ID's can then be used for logging, adding and removing handlers,
and rearranging the order of handlers on a keyword.

Event handlers return a vector result containing a status keyword followed by the result
data. The status keyword indicates how event processing should proceed for events with
multiple handlers, as follows:

  * `:ok` -- No processing errors, pass data to next handler
  * `:ok-go (plus relay event key)` -- No processing errors, do NOT pass to next handler, but fire relay event instead
  * `:fail` -- Error during processing, do not pass to next handler
  * `:fail-go (plus relay event key)` -- Error during processing, fire event to trigger app-level error handling

These different status levels allow you to exercise a certain amount of flexibility in
logging events, throwing exceptions, re-routing events, or other special processing, but the
basic usage boils down to three main cases: either you move on to the next handler, if any,
or you fire a new event, or you return to the function that fired the original event.

## Usage

    (ns my.namespace
      (:require [barnum.api :as ev]))

The core functionality of Barnum can be broken down into five types of
tasks.

 * Defining events
 * Registering validation functions
 * Registering event handlers
 * Registering timeouts and timeout handlers on an event
 * Firing events

In addition, Barnum has functions for checking the number of handlers 
assigned to a given event, and for re-arranging the order in which the 
handlers are executed in response to an event.

### Defining events

Events are defined using keywords as the event name. For simple systems,
you can use simple keywords like `:open` or `:init`, but for more complex
systems, you might consider using hyphenated names like `:resource-load`
and `:resource-start`. To add an event definition, use the `add-event`
function

    (ev/add-event event-key docstring options)

The docstring and options arguments are optional. The docstring argument
describes the purpose of the event, and can be retrieved at the REPL
using `(ev/docs :event-key)`.

TIP: Use namespaced keywords to organize complex event hierarchies. You
do not need to create the actual namespace to use it in a namespaced keyword,
so use as many as you'd like!

The options argument is a map with the following keys:

 * `:min-handlers` - minimum number of handlers required by this event
 * `:max-handlers` - maximum number of handlers required by this event

In an architecture where you call one or more plugins to set up your event
handlers, you can call `(ev/check)` after setup to compare the number
of handlers assigned to each event against the `:min-handlers` (default
zero) and `:max-handlers` (default Integer/MAX_VALUE) for that event.

*Note:* Each event can be defined only once. Attempting to add the same
event key more than once will throw an exception.

Use the `(ev/event-keys)` function to get a list of all currently defined
event keys.

### Adding Validation Functions

Validation functions are added to events, not to event handlers, in order to
ensure consistent semantics (i.e. what each event means). Event handlers, of
course, are free to do their own input validation if needed. Each event can
have one and only one validation function. If you try to assign a validation
function to an event that already has a validation function, the default
behavior is to throw an error unless the override flag is set.

    (ev/add-validation-fn event-key fn :override)

The `:override` keyword is optional. If present, it allows the given function
to silently replace any existing validation function for that event.

### Adding Event Handlers

    (ev/add-handler :my-event :my-handler my-handler-fn)

To register an event handler, call `add-handler` with the event key, a
unique handler key, and the handler function. Each handler function must
take a single argument, which is the map containing the event parameters.
In addition to the arguments you pass to the ev/fire function, the
parameter map will also contain three additional parameters:

 * `:_event` - The event key (in case the same handler handles more than one event)
 * `:_handler` - The handler key of the current handler, for logging, error-handling, etc
 * `:_fired-at` - A Java Date object recording the time the event fired

These additional keys will automatically be added by the barnum ev/fire function.

### Managing Event Handlers

You can use `(ev/remove-handler event-key handler-key)` to remove a handler,
or `(ev/replace-handler event-key handler-key new-fn)` to replace an
existing handler. To add a handler and overwrite any existing handler that
might be using the same key, use `(ev/add-or-replace-handler event-key handler-key handler-fn)`.

Handler keys must be unique, but handler functions can be used more than once.
In other words, you cannot register the same handler key more than once for the same event
but you could, if needed, register the same function under more than one handler key.

If you want to register the same handler for multiple events, pass a set of
event keys as the first argument to `add-handler`. For example, to log all
events, you might do something like the following:

    (ev/add-handler
      (into #{} (ev/event-keys))
      :logging
      my-log-fn)

When each event is fired, the handlers registered for that event will be
called in the order they were added.

Use the `check` function to make sure the handlers you have assigned to
your events match the constraints on the events. The `check` function
returns a map of event keys mapped to a list of error messages for that
event, or an empty map if there are no errors.

    (if-let [event-errors (ev/check)]
      (report-errors event-errors)
      (proceed-with-application))

### Writing event handlers

Event handlers return a vector containing a status key, an optional event
key, and a map containing the (modified) event data.

To write an event handler, `require` the `barnum.results` namespace, and then
use the `ok`, `ok-go`, `fail`,  and `fail-go` functions to wrap any data
returned by your handler.

The `ok` function takes one argument: the data to be returned by event handler.
When you return an `ok` result, Barnum will take the data generated by your
handler, and then pass it to the next event handler for the current event,
or return it to the function that fired the original event, if there are no
more handlers.

    (ns my.handler-ns
      (:require [barnum.api :refer [ok ok-go fail fail-go]]))

    (defn my-handler [args]
      (let [data (:data args)]
        (if data
          (ok args)
          (ok (assoc args :data :default-value))))

The `ok-go` function takes two arguments: the event key for the next event to
fire, and the data to be returned by your handler. When you return an `ok-go`
result, Barnum will fire the event you specify, passing your data as the event
data. Note that this event will fire /after/ the current handler returns, to
prevent stack overflow errors. The data you return will be passed to whatever
validation function may be attached to the event you specify.

    (defn my-other-handler [args]
      (let [data (:data args)
            user (:user data)]
        (if (nil? user)
          (ok-go :authn/login-required data)
          (ok-go :view-profile data))))


The `fail` function takes two arguments: an error message, and the data to be
returned by your handler. When you return a `fail` result, Barnum wraps the
error message into a special data structure that it includes in your data under
the key `:barnum.errors/handler-errors`, and then returns the data to the function
that fired the original event. If there are any other handlers for the current
event, they will _not_ be executed; `fail` returns immediately to the calling
function.

The `:barnum.errors/handler-errors` key points to a vector of tuples containing the
event handler key and the text of the error message. For example, if you had an
event named `:my-risky-event`, and you registered a handler named `:my-error-prone-handler`,
returning `(fail "it died" data)` would produce a value for `data` that looked
like this:

    {:barnum.errors/handler-errors [[:my-error-prone-handler "it died"]]
     ; whatever else was in data
     }

If the data already has a value stored under `:barnum-errors/handler-errors`,
the new error message will be concatenated onto the list by adding the new tuple
to the end of the list. For example, if the data above were returned to a function
that decided to also call `fail`, you might end up with data that looked something
like this:

    {:barnum.errors/handler-errors [[:my-error-prone-handler "it died"]
                                    [:my-parent-handler "error-prone-handler died"]]
     ; whatever else was in the data
    }

The `fail-go` function takes three arguments: the event key of the next event to
fire, an error message, and the data returned by your handler. When you return a
`fail-go` result, Barnum wraps up the error message just as for a `fail` result,
and then immediately fires the event you specify, passing it the data you return.
Normally, you wouldn't put a validation function on a handler for a failure event,
because you don't want to fail to handle a failure (!), but if there is one,
Barnum will pass it your data before calling the event handler(s) for the event
you specify.

Inside a handler function, you can do anything you like, including firing
off other events. Beware of nesting your events too deeply, however, or
firing event A, whose handler fires event B, whose handler fires event A
again. If you need any kind of nested or cyclical event sequence, use
`ok-go` results to keep the stack from overflowing.

### Changing the order of event handlers

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

/TODO:/ use core.async instead of futures?

To trigger an event and execute its associated handlers, use the `fire`
function. The `fire` function takes the event key as its first argument,
followed by zero or more key/value pairs.

    (ev/fire :some-event :data-1 "Some data", :data-2 "More data")

The `fire` function returns the collected results of all the handlers 
for that event, in last-to-first order. To get the result of the last 
handler to fire, just call `first` on the seq. By default, events are
handled synchronously, but you can easily trigger asynchronous event
handling by wrapping `fire` inside a `future`.

NOTES: Don't like that last-to-first vector. The event engine should
treat data like a pipeline or assembly line, where the same product
gets passed from station to station, being modified as it goes, and
then drops off at the end in a completed state.

NOTES: The `fire` fn should pass Barnum metadata to the event handler
as the last argument, so that the handler can grab the current event

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

## ToDo

 * Barnum should also add automatic handler logging by associating a vector
   of handler keys and status results under a key like `:barnum.log/handlers.

## License

Copyright © 2013-2015 Mark Nutter

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
