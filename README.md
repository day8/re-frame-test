# re-frame-test

[![CircleCI](https://circleci.com/gh/Day8/re-frame-test.svg?style=svg)](https://circleci.com/gh/Day8/re-frame-test)

`re-frame-test` is a library which provides helper functions for testing re-frame projects

## Philosopy 
When testing a re-frame application you often what to test the effects of events
you have written on the state of the application.

In general a re-frame test would

 1. subscribe to some state
 2. assert the initial state
 3. dispatch an event
 4. assert the the state has changed
 
 
## Implementation

`re-frame-test` provides two macros that together with `cljs.test` are useful to write tests with the above 
philosophy. 
 
### run-test-sync
Execute `body` as a test, where each `dispatch` call is executed
synchronously (via `dispatch-sync`), and any subsequent dispatches which are
caused by that dispatch are also fully handled/executed prior to control flow
returning to your test.

Think of it kind of as though every `dispatch` in your app had been magically
turned into `dispatch-sync`, and re-frame had lifted the restriction that says
you can't call `dispatch-sync` from within an event handler.

This macro is applicable for most events that do not run async behaviour within the 
event.

From the todomvc example:

    (deftest basic--sync
      (rf-test/run-test-sync
        (rf/dispatch [:initialise-db])
        
Use the `run-test-sync` macro to construct the tests and initialise the app state
note that the `dispatch` will be handled before the following code is executed, 
effectively turning it into a `dispatch-sync`
    
        (let [showing         (rf/subscribe [:showing])
              sorted-todos    (rf/subscribe [:sorted-todos])
              todos           (rf/subscribe [:todos])
              visible-todos   (rf/subscribe [:visible-todos])
              all-complete?   (rf/subscribe [:all-complete?])
              completed-count (rf/subscribe [:completed-count])
              footer-counts   (rf/subscribe [:footer-counts])]
              
Define subscriptions to the app state

          (is (= :all @showing))
          (is (empty? @sorted-todos))
          (is (empty? @todos))
          (is (empty? @visible-todos))
          (is (= false (boolean @all-complete?)))
          (is (= 0 @completed-count))
          (is (= [0 0] @footer-counts))
          
Assert the initial state
    
          (rf/dispatch [:add-todo "write first test"])
          
Dispatch the event that you want to test, remember that `re-frame-test` will process
this event immediately.

          (is (= 1 (count @todos) (count @visible-todos) (count @sorted-todos)))
          (is (= 0 @completed-count))
          (is (= [1 0] @footer-counts))
          (is (= {:id 1, :title "write first test", :done false}
                 (first @todos)))
                 
Test that the dispatch has mutated the state in the way that we expect.
    
### run-test-async
Run `body` as an async re-frame test. The async nature means you'll need to
use `wait-for` any time you want to make any assertions that should be true
*after* an event has been handled.  It's assumed that there will be at least
one `wait-for` in the body of your test (otherwise you don't need this macro
at all).

Note: unlike regular ClojureScript `cljs.test/async` tests, `wait-for` takes
care of calling `(done)` for you: you don't need to do anything specific to
handle the fact that your test is asynchronous, other than make sure that all
your assertions happen with `wait-for` blocks.

This macro is applicable for events that do run some async behaviour (usually outside or re-frame)
within the event.

From the todomvc example:

    (deftest basic--async
      (rf-test/run-test-async
        (rf/dispatch-sync [:initialise-db])
        
Use the `run-test-async` macro to construct the tests and initialise the app state
note that the `dispatch-sync` must be used as this macro does not run the dispatch
immediately like `run-test-sync`.

    
        (let [showing         (rf/subscribe [:showing])
              sorted-todos    (rf/subscribe [:sorted-todos])
              todos           (rf/subscribe [:todos])
              visible-todos   (rf/subscribe [:visible-todos])
              all-complete?   (rf/subscribe [:all-complete?])
              completed-count (rf/subscribe [:completed-count])
              footer-counts   (rf/subscribe [:footer-counts])]
          
Define subscriptions to the app state

          (is (= :all @showing))
          (is (empty? @sorted-todos))
          (is (empty? @todos))
          (is (empty? @visible-todos))
          (is (= 0 @completed-count))
          
Assert the initial state
    
          (rf/dispatch [:add-todo "write first test"])
          
Dispatch the event that you want to test, remember that re-frame will not process
this event immediately, and need to use the `wait-for` macro to continue the tests.
          
          (rf-test/wait-for [:add-todo]
          
Wait for the `:add-todo` event to be dispatched (note, in the 
use of this macro we would typically wait for an event that had been triggered
by the successful return of the async event).        
          
            (is (= [{:id 1, :title "write first test", :done false}] @todos))
            
Test that the dispatch has mutated the state in the way that we expect.    



## Running the CLJS tests with Karma

You will need `npm`, with:

    npm install -g karma karma-cli karma-cljs-test karma-junit-reporter karma-chrome-launcher

And you will need Chrome.


## License

Copyright (c) 2016 Mike Thompson

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
