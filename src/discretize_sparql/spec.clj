(ns discretize-sparql.spec
  (:require [sparclj.core :as sparql]
            [clojure.spec :as s])
  (:import (java.io File)
           (org.apache.commons.validator.routines UrlValidator)))

(def urn?
  (partial re-matches #"(?i)^urn:[a-z0-9][a-z0-9-]{0,31}:[a-z0-9()+,\-.:=@;$_!*'%/?#]+$"))

(def valid-url?
  "Test if `url` is valid."
  (let [validator (UrlValidator. UrlValidator/ALLOW_LOCAL_URLS)]
    (fn [url]
      (.isValid validator url))))

; Reusable specs

(s/def ::positive-int
  (s/and int? pos?))

(s/def ::iri
  (s/and string? valid-url?))

(s/def ::urn
  (s/and string? urn?))

; Specific specs

(s/def ::graph
  (s/or :iri ::iri
        :urn ::urn))

(s/def ::help?
  boolean?)

(s/def ::bins
  ::positive-int)

(s/def ::method
  #{:equidistance :equifrequency :equisize})

(s/def ::min-support
  (s/or :relative-support (s/and double? (partial <= 0) (partial >= 1))
        :absolute-support integer?))

(s/def ::operation
  (partial instance? File))

(s/def ::strict?
  boolean?)

(s/def ::config-bins
  (s/keys :req [::bins ::method ::operation ::sparql/page-size ::sparql/url]
          :opt [::sparql/auth ::graph ::help? ::sparql/parallel? ::strict? ::sparql/update-url]))

(s/def ::config-min-support
  (s/keys :req [::method ::min-support ::operation ::sparql/page-size ::sparql/url]
          :opt [::sparql/auth ::graph ::help? ::sparql/parallel? ::strict? ::sparql/update-url]))

(s/def ::config
  (s/or :bins ::config-bins
        :min-support ::config-min-support))
