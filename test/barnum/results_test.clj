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
      (res/fail "Error" dummy-data)
      => (just [:fail (assoc dummy-data :barnum.errors/errors ["Error"])]))

(fact "about the base fail-go fn"
      (res/fail-go :error-event "Error" dummy-data)
      => (just [:fail (assoc dummy-data :barnum.results/error-event :error-event :barnum.errors/errors ["Error"])])

      (res/fail-go dummy-data "Error" :error-event)
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
      (api/fail "Error" dummy-data)
      => (just [:fail (assoc dummy-data :barnum.errors/errors ["Error"])]))

(fact "about the api fail-go fn"
      (api/fail-go :error-event "Error" dummy-data)
      => (just [:fail (assoc dummy-data :barnum.results/error-event :error-event :barnum.errors/errors ["Error"])])

      (api/fail-go dummy-data "Error" :error-event)
      => (throws Exception #"Expected event key for error event to fire; got class clojure.lang.PersistentHashMap"))