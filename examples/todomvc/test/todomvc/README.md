# Testing with re-frame-test

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
    
    
