(ns todomvc.core-test
  (:require [cljs.test :refer-macros [deftest is]]
            [day8.re-frame.test :as rf-test]
            [re-frame.core :as rf]
            todomvc.db
            todomvc.events
            todomvc.subs))

(defn test-fixtures
  []
  ;; change this coeffect to make tests start with nothing
  (rf/reg-cofx
    :local-store-todos
    (fn [cofx _]
      "Read in todos from localstore, and process into a map we can merge into app-db."
      (assoc cofx :local-store-todos
                  (sorted-map)))))

(deftest basic--sync
  (rf-test/run-test-sync
    (test-fixtures)
    (rf/dispatch [:initialise-db])

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

      (rf/dispatch [:add-todo "write second teXt"])
      (is (= 2 (count @todos) (count @visible-todos) (count @sorted-todos)))
      (is (= 0 @completed-count))
      (is (= [2 0] @footer-counts))
      (is (= {:id 2, :title "write second teXt", :done false}
             (second @todos)))

      (rf/dispatch [:save 2 "write second test"])
      (is (= 2 (count @todos) (count @visible-todos) (count @sorted-todos)))
      (is (= 0 @completed-count))
      (is (= [2 0] @footer-counts))
      (is (= {:id 2, :title "write second test", :done false}
             (second @todos)))

      (rf/dispatch [:toggle-done 1])
      (is (= 2 (count @todos) (count @visible-todos) (count @sorted-todos)))
      (is (= 1 @completed-count))
      (is (= [1 1] @footer-counts))
      (is (= {:id 1, :title "write first test", :done true}
             (first @todos)))

      (rf/dispatch [:set-showing :active])
      (is (= :active @showing))
      (is (= 2 (count @todos) (count @sorted-todos)))
      (is (= 1 (count @visible-todos)))
      (is (= 1 @completed-count))
      (is (= [1 1] @footer-counts))
      (is (= {:id 2, :title "write second test", :done false}
             (first @visible-todos)))

      (rf/dispatch [:set-showing :done])
      (is (= :done @showing))
      (is (= 2 (count @todos) (count @sorted-todos)))
      (is (= 1 (count @visible-todos)))
      (is (= 1 @completed-count))
      (is (= [1 1] @footer-counts))
      (is (= {:id 1, :title "write first test", :done true}
             (first @visible-todos)))

      (rf/dispatch [:toggle-done 2])
      (is (= true (boolean @all-complete?))))))



(deftest basic--async
  (rf-test/run-test-async
    (test-fixtures)
    (rf/dispatch-sync [:initialise-db])

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
         (rf-test/wait-for [:add-todo]

          ;;Test that the dispatch has mutated the state in the way
          ;;that we expect.
          (is (= [{:id 1, :title "write first test", :done false}] @todos))

          (rf/dispatch [:save 2 "write second test"])
          (rf-test/wait-for [:save]
            (is (= "write second test" (:title (second @todos))))

            (rf/dispatch [:toggle-done 1])
            (rf-test/wait-for [:toggle-done]
              (is (= true (:done (first @todos))))

              (rf/dispatch [:set-showing :active])
              (rf-test/wait-for [:set-showing]
                (is (= :active @showing))
                (is (= 1 (count @visible-todos)))
                (is (= 1 @completed-count))
                (is (= {:id 2, :title "write second test", :done false}
                       (first @visible-todos)))

                (rf/dispatch [:set-showing :done])
                (rf-test/wait-for [:set-showing]
                  (is (= :done @showing))
                  (is (= 1 (count @visible-todos)))
                  (is (= {:id 1, :title "write first test", :done true}
                         (first @visible-todos))))))))))))
