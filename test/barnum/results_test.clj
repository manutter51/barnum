(ns barnum.results-test
  (:require [midje.sweet :refer :all]
            [barnum.api :as api]
            [barnum.results :as res]))

(def dummy-data {:key "value"})

(fact "about the base ok fn"
      (res/ok dummy-data)
      => (just [:ok dummy-data]))

(fact "about the base ok-go fn"
      (res/ok-go :next-event dummy-data)
      => (just [:ok (assoc dummy-data :barnum.results/next-event :next-event)])

      (res/ok-go dummy-data :next-event)
      => (throws Exception #"Expected event key for next event to fire; got class clojure.lang.PersistentHashMap"))

(fact "about the base fail fn"
      (res/fail dummy-data)
      => (just [:fail dummy-data]))

(fact "about the base fail-go fn"
      (res/fail-go :error-event dummy-data)
      => (just [:fail (assoc dummy-data :barnum.results/error-event :error-event)])

      (res/fail-go dummy-data :error-event)
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