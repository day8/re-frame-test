<!--
[![CI](https://github.com/day8/re-frame-test/workflows/ci/badge.svg)](https://github.com/day8/re-frame-test/actions?workflow=ci)
[![CD](https://github.com/day8/re-frame-test/workflows/cd/badge.svg)](https://github.com/day8/re-frame-test/actions?workflow=cd)
[![GitHub tag (latest by date)](https://img.shields.io/github/v/tag/day8/re-frame-test?style=flat)](https://github.com/day8/re-frame-test/tags)
[![GitHub pull requests](https://img.shields.io/github/issues-pr/day8/re-frame-test)](https://github.com/day8/re-frame-test/pulls)
-->
[![Clojars Project](https://img.shields.io/clojars/v/day8.re-frame/test?style=for-the-badge&logo=clojure&logoColor=fff)](https://clojars.org/day8.re-frame/test)
[![GitHub issues](https://img.shields.io/github/issues-raw/day8/re-frame-test?style=for-the-badge&logo=github)](https://github.com/day8/re-frame-test/issues)
[![License](https://img.shields.io/github/license/day8/re-frame-test?style=for-the-badge)](LICENSE)

# re-frame-test

This library provides utilities 
for testing [re-frame applications](https://github.com/day8/re-frame).

These utilities:
  - allow you to use `subscribe` in your tests
  - allow you to use `dispatch` in your tests
  - allow you to run tests on both the JVM and JS platforms
  - allow you to create "end to end" integration tests, involving backend servers

For context, please be sure to read the 
[basic testing tutorial](https://github.com/day8/re-frame/blob/master/docs/Testing.md) 
in the main re-frame docs before going any further.

This library primarily supports the testing of Event Handlers, but Subscription Handlers
get to come along for the ride.

## Quick Start Guide

### Step 1. Add Dependency

Add the following project dependency: <br>
[![Clojars Project](https://img.shields.io/clojars/v/day8.re-frame/test.svg)](https://clojars.org/day8.re-frame/test)

Requires re-frame >= "1.1.1"

### Step 2. Registration And Use

In the namespace where you register your tests, perhaps called `tests.cljs`, you have 2 things to do.

**First**, add this require to the `ns`:
```clj
(ns app.tests
  (:require
    ...
    [day8.re-frame.test :as rf-test]   ;; <-- add this
    ...))
```

**Second**, Define or Modify some tests. 
```Clojure
(deftest init
  (rf-test/run-test-sync               ;; <-- add this 
    ;; with the above macro this becomes a dispatch-sync 
    ;; and app-db is isolated between tests
    (rf/dispatch [:initialise-db])   
    ;; Define subscriptions to the app state
    (let [showing         (rf/subscribe [:showing])] 
      ;;Assert the initial state
      (is (= :all @showing)))))
```

## How It Works

`re-frame-test` provides two macros which dovetail with `cljs.test`.
 
### run-test-sync

Execute `body` as a test, where each `dispatch` call is executed
synchronously (via `dispatch-sync`), and any subsequent dispatches which are
caused by that dispatch are also fully handled/executed prior to control flow
returning to your test.

Think of it as though every `dispatch` in your app had been magically
turned into `dispatch-sync`, and re-frame had lifted the restriction that says
you can't call `dispatch-sync` from within an event handler.

This macro is applicable for most events that do not run async behaviour within the 
event.

From the todomvc example:

```Clojure
(defn test-fixtures
  []
  ;; change this coeffect to make tests start with nothing
  (rf/reg-cofx
    :local-store-todos
    (fn [cofx _]
      "Read in todos from localstore, and process into a map we can merge into app-db."
      (assoc cofx :local-store-todos
                  (sorted-map)))))
```

Define some test-fixtures. In this case we have to ignore the localstore
in the tests.

```Clojure
(deftest basic--sync
  (rf-test/run-test-sync
    (test-fixtures)
    (rf/dispatch [:initialise-db])
```

Use the `run-test-sync` macro to construct the tests and initialise the app state.
Note that, the `dispatch` will be handled before the following code is executed, 
effectively turning it into a `dispatch-sync`. Also any changes to the database
and registrations will be rolled back at the termination of the test, therefore 
our fixtures are run within the `run-test-sync` macro.

```Clojure
        ;; Define subscriptions to the app state
        (let [showing         (rf/subscribe [:showing])
              sorted-todos    (rf/subscribe [:sorted-todos])
              todos           (rf/subscribe [:todos])
              visible-todos   (rf/subscribe [:visible-todos])
              all-complete?   (rf/subscribe [:all-complete?])
              completed-count (rf/subscribe [:completed-count])
              footer-counts   (rf/subscribe [:footer-counts])] 
                 
          ;;Assert the initial state
          (is (= :all @showing))
          (is (empty? @sorted-todos))
          (is (empty? @todos))
          (is (empty? @visible-todos))
          (is (= false (boolean @all-complete?)))
          (is (= 0 @completed-count))
          (is (= [0 0] @footer-counts)) 
             
          ;;Dispatch the event that you want to test, remember that `re-frame-test` 
          ;;will process this event immediately.
          (rf/dispatch [:add-todo "write first test"])
              
          ;;Test that the dispatch has mutated the state in the way that we expect.
          (is (= 1 (count @todos) (count @visible-todos) (count @sorted-todos)))
          (is (= 0 @completed-count))
          (is (= [1 0] @footer-counts))
          (is (= {:id 1, :title "write first test", :done false}
                 (first @todos)))
```
    
### run-test-async

This macro is applicable for events that do run some async behaviour 
(usually outside or re-frame, e.g. an http request) within the event.

Run `body` as an async re-frame test. The async nature means you'll need to
use `wait-for` any time you want to make any assertions that should be true
*after* an event has been handled.  It's assumed that there will be at least
one `wait-for` in the body of your test (otherwise you don't need this macro
at all).

Note: unlike regular ClojureScript `cljs.test/async` tests, `wait-for` takes
care of calling `(done)` for you: you don't need to do anything specific to
handle the fact that your test is asynchronous, other than make sure that all
your assertions happen with `wait-for` blocks.

From the todomvc example:

```Clojure
(deftest basic--async
  (rf-test/run-test-async
    (test-fixtures)
    (rf/dispatch-sync [:initialise-db])
```

Use the `run-test-async` macro to construct the tests and initialise the app state
note that the `dispatch-sync` must be used as this macro does not run the dispatch
immediately like `run-test-sync`. Also any changes to the database
and registrations will be rolled back at the termination of the test, therefore
our fixtures are run within the `run-test-async` macro.

```Clojure    
    ;;Define subscriptions to the app state
    (let [showing         (rf/subscribe [:showing])
          sorted-todos    (rf/subscribe [:sorted-todos])
          todos           (rf/subscribe [:todos])
          visible-todos   (rf/subscribe [:visible-todos])
          all-complete?   (rf/subscribe [:all-complete?])
          completed-count (rf/subscribe [:completed-count])
          footer-counts   (rf/subscribe [:footer-counts])]
          
      ;;Assert the initial state
      (is (= :all @showing))
      (is (empty? @sorted-todos))
      (is (empty? @todos))
      (is (empty? @visible-todos))
      (is (= 0 @completed-count))
                    
      ;;Dispatch the event that you want to test, remember 
      ;;that re-frame will not process this event immediately, 
      ;;and need to use the `wait-for` macro to continue the tests.
      (rf/dispatch [:add-todo "write first test"])
          
          
      ;;Wait for the `:add-todo` event to be dispatched 
      ;;(note, in the use of this macro we would typically wait for 
      ;;an event that had been triggered by the successful return of 
      ;;the async event).        
      (rf-test/wait-for [:add-todo-finished]
          
        ;;Test that the dispatch has mutated the state in the way 
        ;;that we expect.    
        (is (= [{:id 1, :title "write first test", :done false}] @todos))
```

Here we have assumed that the `:add-todo` event will make some sort of async 
call which will in turn generate an `add-todo-finished` event when it has finished.
This is not actually the case in the example code.

## Running the CLJS tests with Karma

You will need `npm`, with:

```console
$ npm install -g karma karma-cli karma-cljs-test karma-junit-reporter karma-chrome-launcher
```

And you will need Chrome.


## License

Copyright (c) 2016 Mike Thompson

Distributed under the The MIT License (MIT).
