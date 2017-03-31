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

(s/def ::positive-int (s/and int? pos?))

(s/def ::iri (s/and string? valid-url?))

(s/def ::urn (s/and string? urn?))

; Specific specs

(s/def ::graph (s/or :iri ::iri
                     :urn ::urn))

(s/def ::help? boolean?)

(s/def ::intervals ::positive-int)

(s/def ::method any?) ; FIXME

(s/def ::operation (partial instance? File))

(s/def ::strict? boolean?)

(s/def ::config (s/keys :req [::sparql/url ::intervals ::method ::operation ::sparql/page-size]
                        :opt [::sparql/auth ::graph ::help? ::sparql/parallel?
                              ::strict? ::sparql/update-url]))
