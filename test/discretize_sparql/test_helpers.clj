(ns discretize-sparql.test-helpers
  (:import (org.apache.jena.rdf.model Model ModelFactory)
           (org.apache.jena.query QueryExecutionFactory)))

(defn ^Boolean ask-query
  "Execute SPARQL ASK `query` on RDF `model`."
  ([^Model model
    ^String query]
   (with-open [qexec (QueryExecutionFactory/create query model)]
     (.execAsk qexec)))
  ([^String query]
   (ask-query (ModelFactory/createDefaultModel) query)))
