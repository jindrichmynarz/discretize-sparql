(ns discretize-sparql.core
  (:require [discretize-sparql.util :as util]
            [discretize-sparql.prefix :as prefix]
            [discretize-sparql.spec :as spec]
            [discretize-sparql.endpoint :refer [endpoint]]
            [discretize-sparql.discretize :as discretize]
            [sparclj.core :as sparql]
            [slingshot.slingshot :refer [throw+]])
  (:import (org.apache.jena.update Update UpdateFactory)
           (org.apache.jena.query Query QueryParseException)
           (org.apache.jena.sparql.core Quad Var)
           (org.apache.jena.sparql.syntax ElementData ElementFilter ElementGroup
                                          ElementNamedGraph ElementSubQuery PatternVars)
           (org.apache.jena.sparql.expr E_GreaterThan E_GreaterThanOrEqual
                                        E_IsNumeric
                                        E_LessThanOrEqual E_LessThan
                                        E_LogicalAnd E_LogicalNot E_LogicalOr
                                        E_SameTerm ExprVar)
           (org.apache.jena.sparql.expr.nodevalue NodeValueNode)
           (org.apache.jena.sparql.engine.binding BindingHashMap)
           (org.apache.jena.graph NodeFactory)
           (org.apache.jena.datatypes.xsd XSDDatatype)
           (org.apache.jena.shared.uuid JenaUUID)
           (org.apache.jena.sparql.modify.request QuadDataAcc UpdateDataInsert UpdateWithUsing)))

; Interval bounds expressed via classes from the SemanticScience ontology

(def ^:private left-closed (prefix/sio "001254"))

(def ^:private left-open (prefix/sio "001251"))

(def ^:private right-closed (prefix/sio "001253"))

(def ^:private right-open (prefix/sio "001252"))

; ----- Private functions -----

(defn- ->decimal
  "Convert `number` to a xsd:decimal literal."
  [number]
  (NodeFactory/createLiteral (str number) XSDDatatype/XSDdecimal))

(defn- uuid
  "Generate a UUID URN."
  []
  (NodeFactory/createURI (.asURN (JenaUUID/generate))))

(defn- ^ExprVar expr-var
  "Get variable expression from `var-name`."
  [^String var-name]
  (ExprVar. (Var/alloc var-name)))

(defn- get-quad-variables
  "Extract variables from a `quad`."
  [quad]
  (filter (partial instance? Var)
          [(.getGraph quad)
           (.getSubject quad)
           (.getPredicate quad)
           (.getObject quad)]))

(defn has-inserted-interval?
  "Test if `operation` inserts the variable ?interval."
  [^Update operation]
  (let [interval (Var/alloc "interval")
        insert-quads (.getInsertQuads operation)]
    (contains? (set (mapcat get-quad-variables insert-quads)) interval)))

(defn has-value-variable?
  "Test if `operation` has the ?value variable in the WHERE clause."
  [^Update operation]
  (contains? (PatternVars/vars (.getWherePattern operation)) (Var/alloc "value")))

(defn validate-operation
  "Validate required variables in `operation`."
  [^Update operation]
  (when-not (has-inserted-interval? operation) (throw+ {:type ::util/missing-inserted-interval}))
  (when-not (has-value-variable? operation) (throw+ {:type ::util/missing-value-variable})))

(defn parse-update
  "Parse SPARQL Update operation from `update-string`."
  [^String update-string]
  (when (empty? update-string) (throw+ {:type ::util/empty-operation}))
  (try
    (let [[operation & tail] (.getOperations (UpdateFactory/create update-string))]
      (when (seq tail) (throw+ {:type ::util/multiple-operations}))
      operation)
    (catch QueryParseException ex
      (throw+ {:type ::util/invalid-operation
               :message (.getMessage ex)}))))

(defn- clone-update
  "Clone SPARQL Update `update-operation` by serializing and parsing."
  [^Update update-operation]
  (parse-update (str update-operation)))

(defn all-values-numeric?
  "Test if all values selected in SPARQL Update `operation` are numeric."
  [^Update operation]
  (let [operation' (clone-update operation)
        non-numeric-filter (ElementFilter. (E_LogicalNot. (E_IsNumeric. (ExprVar. (Var/alloc "value")))))
        where-pattern (cond->> (doto (.getWherePattern operation')
                                 (.addElementFilter non-numeric-filter))
                        (instance? UpdateWithUsing operation') (ElementNamedGraph. (.getWithIRI operation')))
        query (.serialize (doto (Query.)
                            (.setQueryAskType)
                            (.setQueryPattern where-pattern)))]
    (not (sparql/ask-query endpoint query))))

(defn get-select-query-fn
  "Generate a function for paged SPARQL SELECT queries from SPARQL Update `operation`."
  [^Update operation]
  (let [operation' (clone-update operation)
        value (Var/alloc "value")
        numeric-filter (ElementFilter. (E_IsNumeric. (ExprVar. value)))
        ; Wrap WHERE pattern in GRAPH if WITH is used.
        where-pattern (cond->> (doto (.getWherePattern operation')
                                 (.addElementFilter numeric-filter))
                        (instance? UpdateWithUsing operation') (ElementNamedGraph. (.getWithIRI operation')))
        variables [value] ; Can be potentially extended with ?resource if needed.
        sub-query (doto (Query.)
                    (.setQuerySelectType)
                    (.setQueryPattern where-pattern)
                    (.addProjectVars variables)
                    (.addOrderBy value 1))
        query (doto (Query.)
                (.setQuerySelectType)
                (.setQueryPattern (doto (ElementGroup.)
                                    (.addElement (ElementSubQuery. sub-query))))
                (.addProjectVars variables))]
    (fn [[limit offset]]
      (.serialize (doto query
                    (.setLimit limit)
                    (.setOffset offset))))))

(defn get-values
  "Get values to discretize using SPARQL SELECT queries
  generated from SPARQL Update `operation`."
  [^Update operation
   & {::sparql/keys [parallel?]}]
  (let [select-fn (get-select-query-fn operation)]
    (map (comp double :value)
         (sparql/select-paged endpoint select-fn ::sparql/parallel? parallel?))))

(defn add-uuids
  "Add UUID URNs to `intervals`."
  [intervals]
  (map (fn [interval] (assoc interval :urn (uuid))) intervals))

(defn round-to-precision
  "Round number `n` to `precision` using `round-fn`."
  [round-fn precision n]
  (let [quotient (Math/pow 10 precision)]
    (/ (round-fn (* n quotient)) quotient)))

(defn round-intervals
  "Round `intervals` for Virtuoso to its maximum supported precision.
  Virtuoso support 13 decimal digits in xsd:decimal."
  [intervals]
  (let [floor (partial round-to-precision #(Math/floor %) 13)
        ceil (partial round-to-precision #(Math/ceil %) 13)
        round-interval (fn [{[left right] :interval
                             :as interval}]
                         (assoc interval :interval [(floor left) (ceil right)]))]
    (if (::sparql/virtuoso? endpoint)
      (map round-interval intervals)
      intervals)))

(defn ^ElementData intervals->values
  "Convert `intervals` to VALUES bindings."
  [intervals]
  (let [min-var (Var/alloc "min")
        left (Var/alloc "left")
        max-var (Var/alloc "max")
        right (Var/alloc "right")
        interval-var (Var/alloc "interval")
        values (doto (ElementData.)
                 (.add min-var)
                 (.add left)
                 (.add max-var)
                 (.add right)
                 (.add interval-var))]
    (doseq [{[min-value max-value] :interval
             :as interval} intervals]
      (.add values (doto (BindingHashMap.)
                     (.add min-var (->decimal min-value))
                     (.add left (if (:left-closed interval) left-closed left-open))
                     (.add max-var (->decimal max-value))
                     (.add right (if (:right-closed interval) right-closed right-open))
                     (.add interval-var (uuid)))))
    values))

(defn get-update-to-intervals
  "Generate SPARQL Update operation to transform values into `intervals` using SPARQL Update `operation`."
  [^Update operation
   intervals]
  (let [operation' (clone-update operation)
        value (expr-var "value")
        min-value (expr-var "min")
        left (expr-var "left")
        max-value (expr-var "max")
        right (expr-var "right")
        numeric-filter (ElementFilter. (E_IsNumeric. value))
        values (intervals->values intervals)
        filter-interval (ElementFilter.
                          (E_LogicalAnd.
                            (E_LogicalOr. (E_LogicalAnd. (E_GreaterThanOrEqual. value min-value)
                                                         (E_SameTerm. left (NodeValueNode. left-closed)))
                                          (E_LogicalAnd. (E_GreaterThan. value min-value)
                                                         (E_SameTerm. left (NodeValueNode. left-open))))
                            (E_LogicalOr. (E_LogicalAnd. (E_LessThanOrEqual. value max-value)
                                                         (E_SameTerm. right (NodeValueNode. right-closed)))
                                          (E_LogicalAnd. (E_LessThan. value max-value)
                                                         (E_SameTerm. right (NodeValueNode. right-open))))))]
    (doto (.getWherePattern operation')
      (.addElementFilter numeric-filter)
      (.addElement values)
      (.addElementFilter filter-interval))
    (str operation')))

(defn intervals->insert-data
  "Convert `intervals` into RDF data for INSERT DATA."
  [^Update operation
   intervals
   & {::spec/keys [graph]}]
  (let [insert-quads (.getInsertQuads operation)
        graph' (cond graph (NodeFactory/createURI graph)
                     (instance? UpdateWithUsing operation) (.getWithIRI operation)
                     (seq insert-quads) (.getGraph (first insert-quads))
                     :else (throw+ {:type ::util/missing-graph}))
        quads (for [{[min-value max-value] :interval
                     :keys [urn]
                     :as interval} intervals
                    :let [quad (fn [predicate object] (Quad. graph' urn predicate object))
                          type-quad (fn [class-node] (quad (prefix/rdf "type") class-node))
                          left-type (if (:left-closed interval) left-closed left-open)
                          right-type (if (:right-closed interval) right-closed right-open)]]
                [(type-quad (prefix/schema "QuantitativeValue"))
                 (type-quad left-type)
                 (type-quad right-type)
                 (quad (prefix/schema "minValue") (->decimal min-value))
                 (quad (prefix/schema "maxValue") (->decimal max-value))])]
    (->> quads
         (apply concat)
         (QuadDataAcc.)
         (UpdateDataInsert.)
         str)))

(defn discretize
  "Run discretization of values from a SPARQL endpoint."
  [{::spec/keys [graph method operation strict?]
    ::sparql/keys [parallel?]
    :as config}]
  (let [parsed-operation (parse-update (slurp operation))]
    (validate-operation parsed-operation)
    (when (and strict? (not (all-values-numeric? parsed-operation)))
      (throw+ {:type ::util/not-all-values-numeric}))
    (let [values (get-values parsed-operation ::sparql/parallel? parallel?)
          intervals (-> config
                        (discretize/discretize values)
                        add-uuids
                        round-intervals)
          update-intervals (get-update-to-intervals parsed-operation intervals)
          insert-intervals (intervals->insert-data parsed-operation intervals ::spec/graph graph)]
      (sparql/update-operation endpoint update-intervals)
      (sparql/update-operation endpoint insert-intervals))))
