(ns rook.spec
  (:require [speclj.core :refer [describe context it run-specs should= with-all should-throw should]]
            [io.aviso.rook :refer [gen-table-routes]]
            [io.pedestal.http.route :as route]
            [io.pedestal.interceptor.chain :as chain]
            [io.pedestal.http.route.definition.table :as table])
  (:import [clojure.lang ExceptionInfo]))

(defn normalize
  "Normalizes the routes so that they can be compared; the interceptors are replaced
  with their names."
  [routes]
  (mapv #(update % 2 (partial map :name))
        routes))

(defn bottom
  [^Throwable e]
  (if-let [nested (.getCause e)]
    (bottom nested)
    e))

(defn get-response
  ([routes path]
   (get-response routes path nil))
  ([routes path request]
   (let [interceptors (-> routes
                          (table/table-routes)
                          (route/router :prefix-tree))]
     (-> {:request (merge {:request-method :get}
                          {:path-info path}
                          request)}
         (chain/enqueue [interceptors])
         chain/execute
         :response))))


(describe "io.aviso.rook"

  (context "single, simple namespace"
    (with-all routes (gen-table-routes {"/items" {:ns 'sample.simple}} nil))

    (it "should generate a single route"
        (should= [["/items" :get [:sample.simple/all-items]]]
                 (normalize @routes)))

    (it "can invoke an endpoint"
        (should= :get-item-response
                 (-> @routes
                     (get-response "/items")))))

  (it "can allow a namespace definition to be just a symbol for the namespace"
      (should= [["/items" :get [:sample.simple/all-items]]]
               (normalize (gen-table-routes {"/items" 'sample.simple} nil))))

  (context "request argument resolver"
    (with-all routes (gen-table-routes {"/widgets" 'sample.request-injection} nil))

    (it "defaults the key name to match the parameter symbol"
        (should= {:arg-value :get}
                 (get-response @routes "/widgets")))

    (it "will use an explicit key if provided"
        (should= {:arg-value "/widgets/specific-key"}
                 (get-response @routes "/widgets/specific-key")))

    (it "requires a non-nil value"
        (let [e (try
                  (get-response @routes "/widgets/failure")
                  (catch Throwable t t))]
          (should (instance? ExceptionInfo e))
          (should= "Resolved argument value was nil." (-> e bottom .getMessage))
          (should= {:context-key-path [:request :does-not-exist]}
                   (-> e bottom ex-data))))))

(run-specs)
