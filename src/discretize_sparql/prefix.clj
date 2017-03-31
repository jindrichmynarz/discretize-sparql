(ns discretize-sparql.prefix
  (:import (org.apache.jena.graph NodeFactory)))

(defn- prefix
  "Builds a function for compact IRIs in the namespace `iri`."
  [iri]
  (fn [local-name]
    (NodeFactory/createURI (str iri local-name))))

(def rdf
  (prefix "http://www.w3.org/1999/02/22-rdf-syntax-ns#"))

(def schema
  (prefix "http://schema.org/"))

(def sio
  (prefix "http://semanticscience.org/resource/SIO_"))

(def sp
  (prefix "http://spinrdf.org/sp#"))

