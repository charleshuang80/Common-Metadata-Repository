(ns ^{:doc "helper to provide the urls to various service endpoints"}
  cmr.system-int-test.utils.url-helper
  (:require [clojure.string :as str]
            [cmr.common.config :as config]
            [cmr.transmit.config :as transmit-config]
            [cmr.elastic-utils.config :as es-config]
            [clj-http.conn-mgr :as conn-mgr]
            [ring.util.codec :as codec]))

(def search-public-protocol (config/config-value :search-public-protocol "http"))
(def search-public-host (config/config-value :search-public-host "localhost"))
(def search-public-port (config/config-value :search-public-port 3003 transmit-config/parse-port))
(def search-relative-root-url (config/config-value :search-relative-root-url ""))

(def dev-system-port
  "The port number for the dev system control api"
  2999)

(def conn-mgr-atom (atom nil))

(defn conn-mgr
  "Returns the HTTP connection manager to use. This allows system integration tests to use persistent
  HTTP connections"
  []
  (when-not @conn-mgr-atom
    (reset! conn-mgr-atom  (conn-mgr/make-reusable-conn-manager {})))

  @conn-mgr-atom)

(defn dev-system-reset-url
  "The reset url on the dev system control api."
  []
  (format "http://localhost:%s/reset" dev-system-port))

(defn dev-system-clear-cache-url
  "The reset url on the dev system control clear cache."
  []
  (format "http://localhost:%s/clear-cache" dev-system-port))

(defn elastic-root
  []
  (format "http://localhost:%s" (es-config/elastic-port)))

(defn create-provider-url
  []
  (format "http://localhost:%s/providers" (transmit-config/metadata-db-port)))

(defn delete-provider-url
  [provider-id]
  (format "http://localhost:%s/providers/%s" (transmit-config/metadata-db-port) provider-id))

(defn reindex-collection-permitted-groups-url
  []
  (format "http://localhost:%s/reindex-collection-permitted-groups"
          (transmit-config/ingest-port)))

(defn cleanup-expired-collections-url
  []
  (format "http://localhost:%s/cleanup-expired-collections"
          (transmit-config/ingest-port)))

(defn ingest-url
  [provider-id type native-id]
  (format "http://localhost:%s/providers/%s/%ss/%s"
          (transmit-config/ingest-port)
          (codec/url-encode provider-id)
          (name type)
          (codec/url-encode native-id)))

(defn search-url
  [type]
  (format "http://localhost:%s/%ss" (transmit-config/search-port) (name type)))

(defn timeline-url
  []
  (format "%s/timeline" (search-url :granule)))

(defn aql-url
  []
  (format "http://localhost:%s/concepts/search" (transmit-config/search-port)))

(defn search-reset-url
  "Clear cache in search app. Only development team to use this functionality."
  []
  (format "http://localhost:%s/reset" (transmit-config/search-port)))

(defn search-clear-cache-url
  "Clear cache in search app."
  []
  (format "http://localhost:%s/clear-cache" (transmit-config/search-port)))

(defn indexer-clear-cache-url
  "Clear cache in indexer app."
  []
  (format "http://localhost:%s/clear-cache" (transmit-config/indexer-port)))

(defn provider-holdings-url
  "Returns the URL for retrieving provider holdings."
  []
  (format "http://localhost:%s/provider_holdings" (transmit-config/search-port)))

(defn retrieve-concept-url
  [type concept-id]
  (format "http://localhost:%s/concepts/%s" (transmit-config/search-port) concept-id))

(defn bulk-index-provider-url
  []
  (format "http://localhost:%s/bulk_index/providers?synchronous=true" (transmit-config/bootstrap-port)))

(defn elastic-refresh-url
  []
  (str (elastic-root) "/_refresh"))

(defn mdb-concept-url
  "URL to concept in mdb."
  ([concept-id]
   (mdb-concept-url concept-id nil))
  ([concept-id revision-id]
   (if revision-id
     (format "http://localhost:%s/concepts/%s/%s" (transmit-config/metadata-db-port) concept-id revision-id)
     (format "http://localhost:%s/concepts/%s" (transmit-config/metadata-db-port) concept-id))))

(defn mdb-concepts-url
  "URL to concept operations in mdb."
  []
  (format "http://localhost:%s/concepts" (transmit-config/metadata-db-port)))

(defn mdb-reset-url
  "Force delete all concepts from mdb."
  []
  (format "http://localhost:%s/reset" (transmit-config/metadata-db-port)))

(defn index-set-reset-url
  "Delete and re-create the index set in elastic. Only development team to use this functionality."
  []
  (format "http://localhost:%s/reset" (transmit-config/index-set-port)))

(defn index-set-health-url
  "URL to check index-set health."
  []
  (format "http://localhost:%s/health" (transmit-config/index-set-port)))

(defn indexer-reset-url
  "Delete and re-create indexes in elastic. Only development team to use this functionality."
  []
  (format "http://localhost:%s/reset" (transmit-config/indexer-port)))

(defn indexer-update-indexes
  "Updates the indexes in the indexer to update mappings and settings"
  []
  (format "http://localhost:%s/update-indexes" (transmit-config/indexer-port)))

;; discard this once oracle impl is in place
(defn mdb-concept-coll-id-url
  "URL to access a collection concept in mdb with given prov and native id."
  [provider-id native-id]
  (format "http://localhost:%s/concept-id/collection/%s/%s"
          (transmit-config/metadata-db-port)
          provider-id
          native-id))

(defn search-root
  "Returns the search url root"
  []
  (let [port (if (empty? search-relative-root-url)
               search-public-port
               (format "%s%s" search-public-port search-relative-root-url))]
    (format "%s://%s:%s/" search-public-protocol search-public-host port)))

(defn location-root
  "Returns the url root for reference location"
  []
  (str (search-root) "concepts/"))
