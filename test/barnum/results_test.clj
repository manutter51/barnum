(ns barnum.results-test
  (:require [midje.sweet :refer :all]
            [barnum.api :as api]
            [barnum.results :refer :all]))

(def dummy-data {:key "value"})

(fact "about the base ok fn"
      (ok dummy-data)
      => (just [:ok dummy-data]))

(fact "about the base ok-go fn"
      (ok-go :next-event dummy-data)
      => (just [:ok (assoc dummy-data :barnum.results/next-event :next-event)])

      (ok-go dummy-data :next-event)
      => (throws Exception #"Expected event key for next event to fire; got class clojure.lang.PersistentHashMap"))

(fact "about the base fail fn"
      (fail dummy-data)
      => (just [:fail dummy-data]))

(fact "about the base fail-go fn"
      (fail-go :error-event dummy-data)
      => (just [:fail (assoc dummy-data :barnum.results/error-event :error-event)])

      (fail-go dummy-data :error-event)
      => (throws Exception #"Expected event key for error event to fire; got class clojure.lang.PersistentHashMap"))

(fact "about the api ok fn"
      (api/ok dummy-data)
      => (just [:ok dummy-data]))

(fact "about the api ok-go fn"
      (api/ok-go :next-event dummy-data)
      => (just [:ok (assoc dummy-data :barnum.results/next-event :next-event)])

      (api/ok-go dummy-data :next-event)
      => (throws Exception #"Expected event key for next event to fire; got class clojure.lang.PersistentHashMap"))

(fact "about the api fail fn"
      (api/fail dummy-data)
      => (just [:fail dummy-data]))

(fact "about the api fail-go fn"
      (api/fail-go :error-event dummy-data)
      => (just [:fail (assoc dummy-data :barnum.results/error-event :error-event)])

      (api/fail-go dummy-data :error-event)
      => (throws Exception #"Expected event key for error event to fire; got class clojure.lang.PersistentHashMap"))