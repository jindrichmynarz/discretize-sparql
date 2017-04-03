(ns discretize-sparql.discretize-test
  (:require [discretize-sparql.discretize :as discretize]
            [discretize-sparql.spec :as spec]
            [clojure.test :refer :all]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]))

(def gen-data
  (gen/fmap sort (-> (gen/double* {:infinite? false :NaN? false})
                     (gen/list-distinct {:min-elements 1}))))

(defspec interval-count 100
  ; Discretization generates the required number of intervals.
  (prop/for-all [[data bins] (gen/bind gen-data
                                       #(gen/tuple (gen/return %) (gen/choose 1 (count %))))
                 method (gen/elements #{:equidistance :equifrequency})]
                (= (count (discretize/discretize #::spec{:bins bins :method method} data)) bins)))
