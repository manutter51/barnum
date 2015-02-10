# Barnum Events

The Barnum event engine provides a generic library for defining arbitrary
events, assigning handlers to those events, and firing events. Event firing
is synchronous, but you can easily wrap the `fire!` function in a future, so
you can have event handlers that return a result that you process whenever
you like.

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
and then return a result. The result will always be a map containing predefined keys
for `:status` and `:data`, as well as any additional keys like `:next` containing the
next event to fire, or `:message` for any error messages. The result map will also
contain a key of `:barnum.events/context` containing information about the details of
event handling, such as which handlers were fired, what time (in milliseconds) each
handler began, and any errors which arose.

The `:next` event key is used to specify the next event to be fired, to avoid the stack
overflow that might occur if one handler fired an event that triggered a handler that
fired an event that triggered a handler, etc. etc. By returning the `:next` event key
from the handler, trampoline-style, each handler can pass data on to the next stage
of processing without tying up a stack frame. If processing is complete and no further
events need to be triggered, simply omit the relay event key, and Barnum will return
the result to whatever process fired the original event.

### Multiple Event Handlers

Sometimes you may want multiple handlers to respond to the same event. For example,
you might want an event that saves a record in a database, makes a log entry, and
sends off an email notification. Or you may have several handlers that need to look at the
data and decide whether to handle the event or hand it off to the next handler, depending
on (for example) the contents of a certain form field. Barnum allows you to handle
such cases by assigning multiple handlers to the same event.

To avoid confusion, each event handler must be given a unique ID in the form of a
keyword. These keyword ID's can then be used for logging, adding and removing handlers,
and rearranging the order of handlers on a keyword.

Event handlers use functions in the `barnum.results` namespace to return a properly-formated
hash map, as described above. The `barnum.results` functions to use are as follows:

  * `(ok data)` -- Success, pass data to next handler
  * `(ok-go next-event-key data)` -- Success, do NOT pass to next handler, but fire specified event instead
  * `(fail error-key error-message data)` -- Error during processing, do not pass to next handler
  * `(fail-go next-event-key error-key error-message data)` -- Error during processing, fire event to trigger app-level error handling

These different status levels allow you to exercise a certain amount of flexibility in
logging events, throwing exceptions, re-routing events, or other special processing, but the
basic usage boils down to three main cases: either you move on to the next handler, if any,
or you fire a new event, or you return to the function that fired the original event.

## Usage

    (ns my.namespace
      (:require [barnum.api :as ev]))

The core functionality of Barnum can be broken down into four types of
tasks.

 * Defining events
 * Registering validation functions
 * Registering event handlers
 * Firing events

In addition, Barnum has functions for checking the number of handlers 
assigned to a given event, and for re-arranging the order in which the 
handlers are executed in response to an event.

### Defining events

Events are defined using keywords as the event name. For simple systems,
you can use simple keywords like `:open` or `:init`, but for more complex
systems, you might consider using namespaced keywords like `:resource/load`
and `:resource/start`. To add an event definition, use the `add-event`
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
zero) and `:max-handlers` (default Integer/MAX_VALUE) for that event. This
way you can set up events that need to be handled by at least one handler
and/or at most one handler. The `(ev/check)` function will return a map of
all events that have errors, and the error(s) for each event.

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
to silently replace any existing validation function for that event. To add the
same validation function to multiple events, pass a set of event keys as the
event-key argument.

### Adding Event Handlers

    (ev/add-handler :my-event :my-handler my-handler-fn)

To register an event handler, call `add-handler` with the event key, a
unique handler key, and the handler function. Each handler function must
take two arguments, a context argument, and a data argument. The context
argument is a map that will contain any application specific context, such
as database connections, loggers, etc. The context map will also contain
the current event key and current handler key, should you wish to refer to
them in your handler (e.g. for logging).

The data argument will contain the event-specific data to be processed by
your event handler.

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

Event handlers must use functions from the `barnum.results` namespace to return the
results produced by the handler.

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


The `fail` function takes three arguments: an error key (similar to an error
code, but why pass an arbitrary number when you can use a human readable key?),
an error message, and the data to be returned by your handler. When you return
a `fail` result, Barnum wraps the arguments into a special data structure that
includes the error key, error message, and data under they keys `:error-key`,
`:message` and `:data`, respectively. If there are any other handlers for the
current event, they will _not_ be executed; a `fail` is returned immediately
to the calling function.

The `:barnum.events/errors` key points to a vector of tuples containing the
system time in millis, the event key, the event handler key, the error key,
and the text of the error message. For example, if you had an event named
`:my-risky-event`, and you registered a handler named `:my-error-prone-handler`,
returning `(fail :some-error "it died" data)` would produce a value for `data`
that looked like this:

    {:barnum.events/errors
      [[1423567342887 :my-risky-event :my-error-prone-handler some-error "it died"]]
     ; whatever else was in data
     }

If the data already has a value stored under `:barnum-events/errors`, the new error
message will be concatenated onto the list by adding the new tuple to the end of the
list.

The `fail-go` function takes four arguments: the event key of the next event to
fire, an error key, an error message, and the data returned by your handler. When
you return a `fail-go` result, Barnum wraps up the error message just as for a `fail`
result, and then immediately fires the event you specify, passing it the data you
return. Normally, you wouldn't put a validation function on a handler for a failure
event, because you don't want to fail to handle a failure (!), but if there is one,
Barnum will pass it your data before calling the event handler(s) for the event
you specify.

Inside a handler function, you can do anything you like, including firing off other
events. Beware of nesting your events too deeply, however, or firing event A, whose
handler fires event B, whose handler fires event A again. If you need any kind of
nested or cyclical event sequence, use `ok-go` results to keep the stack from
overflowing.

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

To trigger an event and execute its associated handlers, use the `fire`
function. The `fire` function takes the event key as its first argument,
followed by zero or more key/value pairs.

    (ev/fire :some-event :data-1 "Some data", :data-2 "More data")

The `fire` function returns the accumulated result of all the handlers
for that event, meaning that if you have 3 handlers on a function, the
data will be handed to the first handler, the results of the first handler
will be handed to the second, the results of the second handed to the
third, and the results of the third returned to your function. That's
assuming all goes well of course--any handler can return an error result
and-or specify a new event to jump to instead of continuing on to the
next handler. You should always examine the `:status` key to determine
whether or not any errors occurred during event handling.

By default, events are handled synchronously, but you can easily trigger
asynchronous event handling by wrapping `fire` inside a `future`.

### Validating event parameters

You can write custom validation functions to be used whenever an event
is fired, to check the parameters being passed along with the event. This
validation function should take two arguments: `ctx` and `args`. The
`ctx` argument is a map of application-specific context values, and the
`args` argument is a map of key-value pairs containing the data to be
processed by the handler. Return a list of validation errors, if any, or
nil if there were no errors.

To assign a validation function to an event, call the `set-validation-fn!`
function, passing in the event key and the validation function. Events
can only have one validation function per event key, and it is an error
to try and assign a validation function to an event that already has one.
To replace an existing validation function, pass `:override` as the third
argument to `set-validation-fn!`.

If you wish to apply the same validation function to multiple events, you
can pass a set of event keys as the first argument.

## ToDo

 * Add functions for reading the event logs and error stacks from the
   Barnum context
 * Add support for multiple validation functions per event, where each
   function receives the results of the function before it.

## License

Copyright © 2013-2015 Mark Nutter

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
