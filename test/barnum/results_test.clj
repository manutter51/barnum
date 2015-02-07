(ns barnum.results-test
  (:require [midje.sweet :refer :all]
            [barnum.api :as api]
            [barnum.results :as res]))

(def dummy-data {:key "value"})

(fact "about the base ok fn"
      (res/ok dummy-data)
      => (exactly {:status :ok :data dummy-data}))

(fact "about the base ok-go fn"
      (res/ok-go :next-event dummy-data)
      => (exactly {:status :ok, :next :next-event, :data dummy-data})

      (res/ok-go dummy-data :next-event)
      => (throws Exception #"Expected event key for next event to fire; got class clojure.lang.PersistentHashMap"))

(fact "about the base fail fn"
      (res/fail :an-error "Error" dummy-data)
      => (exactly {:status :fail :error-key :an-error :message "Error" :data dummy-data}))

(fact "about the base fail-go fn"
      (res/fail-go :error-event :an-error "Error" dummy-data)
      => (exactly {:status :fail, :error-key :an-error, :next :error-event, :message "Error", :data dummy-data})

      (res/fail-go dummy-data :an-error "Error" :error-event)
      => (throws Exception #"Expected event key for error event to fire; got class clojure.lang.PersistentHashMap"))

(fact "about the base not-valid fn"
      (res/not-valid {:missing-key "Missing"} {:key "value"})
      => (exactly {:status :not-valid, :errors {:missing-key "missing"}, :data {:key "value"}}))
