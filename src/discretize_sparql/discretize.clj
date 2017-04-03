(ns discretize-sparql.discretize
  (:require [discretize-sparql.spec :as spec])
  (:import (eu.easyminer.discretization AbsoluteSupport DefaultDiscretization Interval
                                        RelativeSupport SortedIterable)
           (eu.easyminer.discretization.task EquidistanceDiscretizationTask
                                             EquifrequencyDiscretizationTask
                                             EquisizeDiscretizationTask)))

(def ^:private default-buffer-size
  1000000)

(defn- ->sorted-iterator
  "Mark collection `coll` as sorted."
  [coll]
  (reify SortedIterable
    (iterator [_] (.iterator coll))))

(defmulti ->support
  "Wrap minimum support in the appropriate class."
  type)

(defmethod ->support Double
  [support]
  (RelativeSupport. support))

(defmethod ->support Integer
  [support]
  (AbsoluteSupport. support))

(defmulti discretization-task
  "Construct discretization task as per the chosen `method`."
  (fn [{::spec/keys [method]}] method))

(defmethod discretization-task :equidistance
  [{::spec/keys [bins]}]
  (reify EquidistanceDiscretizationTask
    (getNumberOfBins [_] bins)
    (getBufferSize [_] default-buffer-size)))

(defmethod discretization-task :equifrequency
  [{::spec/keys [bins]}]
  (reify EquifrequencyDiscretizationTask
    (getNumberOfBins [_] bins)
    (getBufferSize [_] default-buffer-size)))

(defmethod discretization-task :equisize
  [{::spec/keys [min-support]}]
  (let [support (->support min-support)]
    (reify EquisizeDiscretizationTask
      (getMinSupport [_] support)
      (getBufferSize [_] default-buffer-size))))

(defn- interval->clj
  "Convert `interval` to Clojure data structure."
  [^Interval interval]
  {:interval [(.getLeftBoundValue interval) (.getRightBoundValue interval)]
   :left-closed (.isLeftBoundClosed interval)
   :right-closed (.isRightBoundClosed interval)})

(defn discretize
  "Discretize numeric `data` based on `config`."
  [config data]
  (map interval->clj (.discretize (DefaultDiscretization.)
                                  (discretization-task config)
                                  (->sorted-iterator data)
                                  Double)))
