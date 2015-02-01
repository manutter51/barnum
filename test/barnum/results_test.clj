(ns barnum.results-test
  (:require [midje.sweet :refer :all]
            [barnum.results :refer :all]))

(def dummy-data {:key "value"})

(fact "about the ok fn"
      (ok dummy-data)
      => (just [:ok dummy-data]))

(fact "about the ok-go fn"
      (ok-go :next-event dummy-data)
      => (just [:ok (assoc dummy-data :barnum.results/next-event :next-event)])

      (ok-go dummy-data :next-event)
      => (throws Exception #"Expected event key for next event to fire; got class clojure.lang.PersistentHashMap"))

(fact "about the fail fn"
      (fail dummy-data)
      => (just [:fail dummy-data]))

(fact "about the fail-go fn"
      (fail-go :error-event dummy-data)
      => (just [:fail (assoc dummy-data :barnum.results/error-event :error-event)])

      (fail-go dummy-data :error-event)
      => (throws Exception #"Expected event key for error event to fire; got class clojure.lang.PersistentHashMap"))