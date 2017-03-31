(ns discretize-sparql.endpoint
  (:require [mount.core :as mount :refer [defstate]]
            [sparclj.core :as sparql]))

(defstate endpoint
  :start (sparql/init-endpoint (mount/args)))
