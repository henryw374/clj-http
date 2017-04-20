(ns clj-http.examples.caching-middleware
  (:require
   [clj-http.client :as http]
   [clojure.core.cache :as cache]))

(def http-cache (atom (cache/ttl-cache-factory {} :ttl (* 60 60 1000))))

(defn slurp-bytes
  "Slurp the bytes from a slurpable thing"
  [x]
  (with-open [out (java.io.ByteArrayOutputStream.)]
    (clojure.java.io/copy (clojure.java.io/input-stream x) out)
    (.toByteArray out)))

(defn- cached-response
  ([client req]
   (let [cache-key (str (:server-name req) (:uri req) "?" (:query-string req))]
     (if (cache/has? @http-cache cache-key)
       (do
         (println "CACHE HIT")
         (reset! http-cache (cache/hit @http-cache cache-key)) ; update cache stats
         (cache/lookup @http-cache cache-key)) ; return cached value
         ; do not invoke further middleware
       (do
         (println "CACHE MISS")
         (let [resp (update (client req) :body slurp-bytes)]
           (if (http/success? resp)
             (do
               (reset! http-cache (cache/miss @http-cache cache-key resp)) ; update cache value
               (client req resp nil)) ; invoke next middleware
             (do
               (client req resp nil)))))))))

(defn wrap-caching-middleware
  [client]
  (fn
    ([req]
     (cached-response client req))))

(defn example [& uri]
  (-> (time (http/with-additional-middleware [#'wrap-caching-middleware]
              (http/get (or uri "https://api.github.com")
                        {
                         ;; :debug true
                         ;; :debug-body true
                         ;; :throw-entire-message? true
                         })))
      (select-keys ,,, [:status :reason-phrase])))

;; Try this out:
;;
;; user> (use '[clj-http.examples.caching-middleware :as mw])
;; nil
;; user> (clojure.pprint/pprint (mw/example))
;; CACHE MISS
;; "Elapsed time: 2044.735745 msecs"
;; nil
;; user> (clojure.pprint/pprint (mw/example))
;; CACHE HIT
;; "Elapsed time: 0.89591 msecs"
;; nil
;; user>
