(ns barnum.events.validation
  (:require [beanbag.core :refer [ok fail skip]]
            [clojure.set :as set]))

;; Built-in/generic validation checks for when passing arguments
;; to the events being triggered.
;;
;; Validation functions take 2 args, the params list, and the
;; current args (with defaults pre-applied)

(defn has-key?
  "Returns true if the given map contains the given key"
  [m k]
  (let [marker (fn [] nil) ; anon fn, will never appear as key in args
        found (get m k marker)]
    (when (not= found marker)
      k)))

;; TODO: this test is worthless, because we provide default keys
;; for any param that's not specified. Replace this with a test
;; for all params not-nil
(defn require-all
  "Returns a list containing the keys for all missing parameters,
or an empty list if all keys in the params list are present as keys
in the args list"
  [params args]
  (map #(format "Missing key :%s " (name %))
       (filter #(not (has-key? args %)) params)))

(defn restrict-all
  "Returns a list of all keys in the args map that are not in the
params list"
  [params args]
  (let [args (dissoc args :_event :_handler :_fired-at) ; ignore built-ins
        arg-keys-set (into #{} (keys args))
        param-set (into #{} params)]
    (map #(format "Unexpected key :%s in arg list" (name %))
         (seq (set/difference arg-keys-set param-set)))))
