(ns discretize-sparql.cli
  (:gen-class)
  (:require [discretize-sparql.core :as core]
            [discretize-sparql.spec :as spec]
            [discretize-sparql.util :as util]
            [clojure.spec :as s]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.tools.cli :refer [parse-opts]]
            [mount.core :as mount]
            [slingshot.slingshot :refer [try+]]
            [sparclj.core :as sparql]))

; ----- Private functions -----

(defn- usage
  [summary]
  (util/join-lines ["Discretize numeric literals from a SPARQL endpoint"
                    ""
                    "Usage: discretize_sparql [options]"
                    ""
                    "Options:"
                    summary]))

(defn- error-msg
  [errors]
  (util/join-lines (cons "The following errors occurred while parsing your command:\n" errors)))

(defn- validate-params
  "Validate if params match their spec."
  [params]
  (when-not (s/valid? ::spec/config params)
    (util/die (str "The provided arguments are invalid.\n\n"
                   (s/explain-str ::spec/config params)))))

(defn- main
  [{::sparql/keys [auth url]
    :as params}]
  (validate-params params)
  (try+ (mount/start-with-args params)
        (catch [:type ::sparql/invalid-auth] _
          (util/die (apply format "Username '%s' and password '%s' are invalid." auth)))
        (catch [:type ::sparql/endpoint-not-found] _
          (util/die (format "SPARQL endpoint <%s> was not found." url))))
  (try+ (core/discretize params)
    (catch [:type ::util/empty-operation] _
      (util/die "The provided SPARQL Update operation is empty."))
    (catch [:type ::util/invalid-operation] {:keys [message]}
      (util/die (format "Invalid SPARQL Update operation: \n %s" message)))
    (catch [:type ::util/multiple-operations] _
      (util/die "There are more SPARQL Update operations. Only one is supported."))
    (catch [:type ::util/missing-inserted-interval] _
      (util/die "The INSERT clause is missing the required ?interval variable."))
    (catch [:type ::util/missing-value-variable] _
      (util/die "The WHERE clause is missing the required ?value variable."))
    (catch [:type ::util/not-all-values-numeric] _
      (util/die (str "Not all selected values are numeric. "
                     "Either drop the `--strict` parameter or "
                     "provide a more selective SPARQL Update operation.")))
    (catch [:type ::util/missing-graph] _
      (util/die "Please provide the named graph (--graph) to insert the intervals into."))))

; ----- Private vars -----

(def ^:private cli-options
  [["-e" "--endpoint ENDPOINT" "SPARQL endpoint's URL"
    :id ::sparql/url]
   ["-a" "--auth AUTH" "Endpoint's authorization written as username:password"
    :id ::sparql/auth
    :parse-fn #(string/split % #":")]
   ["-u" "--update UPDATE" "Path to SPARQL Update operation"
    :id ::spec/operation
    :parse-fn io/as-file
    :validate [util/file-exists? "The SPARQL Update operation does not exist."]]
   ["-m" "--method METHOD" "Method of discretization to use."
    :id ::spec/method]
   ["-i" "--intervals INTERVALS" "Number of intervals to generate."
    :id ::spec/intervals
    :parse-fn util/->integer]
   ["-g" "--graph GRAPH" "IRI or URN of the named graph to which intervals will be loaded."
    :id ::spec/graph]
   ["-p" "--page-size PAGE_SIZE" "Number of results to fetch in one request."
    :id ::sparql/page-size
    :parse-fn util/->integer
    :default 10000]
   [nil "--update-url UPDATE_URL" "URL of a SPARQL Update endpoint, if distinct from the query URL"
    :id ::sparql/update-url]
   [nil "--parallel" "Execute queries in parallel."
    :id ::sparql/parallel?
    :default false]
   [nil "--strict" "Fail if not all discretized values are numeric."
    :id ::spec/strict?
    :default false]
   ["-h" "--help" "Display help information."
    :id ::spec/help?
    :default false]])

; ----- Public functions -----

(defn -main
  [& args]
  (let [{{::spec/keys [help?]
          :as params} :options
         :keys [errors summary]} (parse-opts args cli-options)]
    (cond help? (util/info (usage summary))
          errors (util/die (error-msg errors))
          :else (main params))))
