(ns discretize-sparql.core-test
  (:require [discretize-sparql.core :as core]
            [discretize-sparql.test-helpers :refer [ask-query]]
            [clojure.java.io :as io]
            [clojure.test :refer :all]))

(def read-resource
  (comp slurp io/resource))

(def parse-operation
  (comp core/parse-update read-resource))

(deftest parse-update
  (is (parse-operation "valid_update.ru") "Valid SPARQL Update")
  (is (thrown? Exception (parse-operation "invalid_update.ru"))
      "Syntactically invalid SPARQL Update.")
  (is (thrown? Exception (parse-operation "multiple_updates.ru"))
      "Multiple SPARQL Update operations."))

(deftest test-mapping
 (let [query (read-resource "mapping_test.rq")]
   (is (not (ask-query query)) "There must be no values mapped to unexpected intervals.")))

(deftest has-inserted-interval?
  (let [test-fn (comp core/has-inserted-interval? parse-operation)]
    (is (test-fn "valid_update.ru") "Valid operation inserts ?interval.")
    (is (not (test-fn "missing_interval.ru")) "Missing ?interval in INSERT is invalid.")))

(deftest has-value-variable?
  (let [test-fn (comp core/has-value-variable? parse-operation)]
    (is (test-fn "valid_update.ru") "Valid operation projects ?value variable.")
    (is (not (test-fn "missing_value.ru")) "Missing ?value in WHERE is invalid.")))

(deftest round-to-precision
  (is (core/round-to-precision #(Math/floor %) 13 0.00262335315960871) 0.0026233531596)
  (is (core/round-to-precision #(Math/ceil %) 13 0.9933566926437779) 0.9933566926438))
