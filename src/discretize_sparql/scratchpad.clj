(ns discretize-sparql.scratchpad
  (:require [discretize-sparql.core :as core]
            [discretize-sparql.spec :as spec]
            [clojure.java.io :as io]
            [sparclj.core :as sparql]))

(comment
  (def intervals
    (core/add-uuids
      [{:interval [0 1000]
        :left-closed true
        :right-closed false}
       {:interval [1000 3000]
        :left-closed false
        :right-closed true}
       {:interval [3000 4000]
        :left-closed false
        :right-closed true}]))

  (core/intervals->values intervals)

  (def config {::sparql/url "http://lod2-dev.vse.cz:8890/sparql-auth"
               ::spec/intervals 15
               ::spec/operation (io/as-file "wishful_update_agreed_price.ru")
               ::sparql/auth ["dba" "loddva"]
               ::sparql/page-size 1000})
  (def operation (core/parse-update (slurp (::spec/operation config))))
  (def select-fn (core/get-select-query-fn operation))
  (def values (core/get-values config operation))
  (core/all-values-numeric? config operation)
  (take 10 values)
  (println (core/intervals->insert-data operation intervals))

  (core/get-update-to-intervals operation intervals)
  )
