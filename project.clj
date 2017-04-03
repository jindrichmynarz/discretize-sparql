(defproject discretize-sparql "0.1.0"
  :description "Discretize numeric literals in RDF from a SPARQL endpoint"
  :url "http://github.com/jindrichmynarz/discretize-sparql"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :scm {:name "git"
        :url "https://github.com/jindrichmynarz/discretize-sparql"}
  :dependencies [[org.clojure/clojure "1.9.0-alpha14"]
                 [org.clojure/tools.cli "0.3.5"]
                 [sparclj "0.1.7"]
                 [mount "0.1.11"]
                 [slingshot "0.12.2"]
                 [org.apache.jena/jena-core "3.1.1"]
                 [org.apache.jena/jena-arq "3.1.1"]
                 [org.slf4j/slf4j-log4j12 "1.7.24"]
                 [log4j/log4j "1.2.17" :exclusions [javax.mail/mail
                                                    javax.jms/jms
                                                    com.sun.jmdk/jmxtools
                                                    com.sun.jmx/jmxri]]
                 [commons-validator/commons-validator "1.5.1"]
                 [com.github.KIZI/EasyMiner-Discretization "1.0.1"]]
  :repositories [["jitpack" "https://jitpack.io"]]
  :main discretize-sparql.cli
  :profiles {:dev {:dependencies [[org.clojure/test.check "0.9.0"]]
                   :plugins [[lein-binplus "0.4.2"]]}
             :test {:resource-paths ["test/resources"]}
             :uberjar {:aot :all
                       :uberjar-name "discretize_sparql.jar"}}
  :bin {:name "discretize_sparql"})
