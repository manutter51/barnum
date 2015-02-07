(ns barnum.results-test
  (:require [midje.sweet :refer :all]
            [barnum.api :as api]
            [barnum.results :as res]))

(def dummy-data {:key "value"})

(fact "about the base ok fn"
      (res/ok dummy-data)
      => (exactly [:ok dummy-data]))

(fact "about the base ok-go fn"
      (res/ok-go :next-event dummy-data)
      => (exactly [:ok :next-event dummy-data])

      (res/ok-go dummy-data :next-event)
      => (throws Exception #"Expected event key for next event to fire; got class clojure.lang.PersistentHashMap"))

(fact "about the base fail fn"
      (res/fail "Error" dummy-data)
      => (exactly [:fail "Error" dummy-data]))

(fact "about the base fail-go fn"
      (res/fail-go :error-event "Error" dummy-data)
      => (exactly [:fail :error-event "Error" dummy-data])

      (res/fail-go dummy-data "Error" :error-event)
      => (throws Exception #"Expected event key for error event to fire; got class clojure.lang.PersistentHashMap"))

(fact "about the base not-valid fn"
      (res/not-valid {:missing-key "Missing"} {:key "value"})
      => (exactly [:not-valid {:missing-key "missing"} {:key "value"}]))

(fact "about the api ok fn"
      (api/ok dummy-data)
      => (exactly [:ok dummy-data]))

(fact "about the api ok-go fn"
      (api/ok-go :next-event dummy-data)
      => (exactly [:ok :next-event dummy-data])

      (api/ok-go dummy-data :next-event)
      => (throws Exception #"Expected event key for next event to fire; got class clojure.lang.PersistentHashMap"))

(fact "about the api fail fn"
      (api/fail "Error" dummy-data)
      => (exactly [:fail "Error" dummy-data]))

(fact "about the api fail-go fn"
      (api/fail-go :error-event "Error" dummy-data)
      => (exactly [:fail :error-event "Error" dummy-data])

      (api/fail-go dummy-data "Error" :error-event)
      => (throws Exception #"Expected event key for error event to fire; got class clojure.lang.PersistentHashMap"))

(fact "about the api not-valid fn"
      (api/not-valid {:missing-key "Missing"} {:key "value"})
      => (exactly [:not-valid {:missing-key "missing"} {:key "value"}]))
