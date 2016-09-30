(ns cmr.access-control.services.acl-validation
  (:require
    [cheshire.core :as json]
    [clj-time.core :as t]
    [clojure.edn :as edn]
    [cmr.access-control.data.acls :as acls]
    [cmr.access-control.services.auth-util :as auth-util]
    [cmr.access-control.services.group-service :as groups]
    [cmr.access-control.services.messages :as msg]
    [cmr.acl.core :as acl]
    [cmr.common-app.services.search.query-execution :as qe]
    [cmr.common-app.services.search.query-model :as qm]
    [cmr.common.date-time-parser :as dtp]
    [cmr.common.util :as util]
    [cmr.common.validations.core :as v]
    [cmr.transmit.config :as transmit-config]
    [cmr.transmit.echo.tokens :as tokens]
    [cmr.transmit.metadata-db :as mdb1]
    [cmr.transmit.metadata-db2 :as mdb]))

(defn- catalog-item-identity-collection-applicable-validation
  "Validates the relationship between collection_applicable and collection_identifier."
  [key-path cat-item-id]
  (when (and (:collection-identifier cat-item-id)
             (not (:collection-applicable cat-item-id)))
    {key-path ["collection_applicable must be true when collection_identifier is specified"]}))

(defn- catalog-item-identity-granule-applicable-validation
  "Validates the relationship between granule_applicable and granule_identifier."
  [key-path cat-item-id]
  (when (and (:granule-identifier cat-item-id)
             (not (:granule-applicable cat-item-id)))
    {key-path ["granule_applicable must be true when granule_identifier is specified"]}))

(defn- catalog-item-identity-collection-or-granule-validation
  "Validates minimal catalog_item_identity fields."
  [key-path cat-item-id]
  (when-not (or (:collection-applicable cat-item-id)
                (:granule-applicable cat-item-id))
    {key-path ["when catalog_item_identity is specified, one or both of collection_applicable or granule_applicable must be true"]}))

(defn- make-collection-entry-titles-validation
  "Returns a validation for the entry_titles part of a collection identifier, closed over the context and ACL to be validated."
  [context acl]
  (let [provider-id (-> acl :catalog-item-identity :provider-id)]
    (v/every (fn [key-path entry-title]
               (when-not (seq (mdb1/find-concepts context {:provider-id provider-id :entry-title entry-title} :collection))
                 {key-path [(format "collection with entry-title [%s] does not exist in provider [%s]" entry-title provider-id)]})))))

(defn- access-value-validation
  "Validates the access_value part of a collection or granule identifier."
  [key-path access-value-map]
  (let [{:keys [min-value max-value include-undefined-value]} access-value-map]
    (cond
      (and include-undefined-value (or min-value max-value))
      {key-path ["min_value and/or max_value must not be specified if include_undefined_value is true"]}

      (and (not include-undefined-value) (not (or min-value max-value)))
      {key-path ["min_value and/or max_value must be specified when include_undefined_value is false"]})))

(defn temporal-identifier-validation
  "A validation for the temporal part of an ACL collection or granule identifier."
  [key-path temporal]
  (let [{:keys [start-date stop-date]} temporal]
    (when (and start-date stop-date
               (t/after? (dtp/parse-datetime start-date) (dtp/parse-datetime stop-date)))
      {key-path ["start_date must be before stop_date"]})))

(defn- make-collection-identifier-validation
  "Returns a validation for an ACL catalog_item_identity.collection_identifier closed over the given context and ACL to be validated."
  [context acl]
  {:entry-titles (v/when-present (make-collection-entry-titles-validation context acl))
   :access-value (v/when-present access-value-validation)
   :temporal (v/when-present temporal-identifier-validation)})

(def granule-identifier-validation
  "Validation for the catalog_item_identity.granule_identifier portion of an ACL."
  {:access-value (v/when-present access-value-validation)
   :temporal (v/when-present temporal-identifier-validation)})

(def ^:private c "create")
(def ^:private r "read")
(def ^:private u "update")
(def ^:private d "delete")

(def ^:private grantable-permission-mapping
  {:single-instance-identity {"GROUP_MANAGEMENT"                [u d]}
   :provider-identity        {"AUDIT_REPORT"                    [r]
                              "OPTION_ASSIGNMENT"               [c r d]
                              "OPTION_DEFINITION"               [c d]
                              "OPTION_DEFINITION_DEPRECATION"   [c]
                              "DATASET_INFORMATION"             [r]
                              "PROVIDER_HOLDINGS"               [r]
                              "EXTENDED_SERVICE"                [c u d]
                              "PROVIDER_ORDER"                  [r]
                              "PROVIDER_ORDER_RESUBMISSION"     [c]
                              "PROVIDER_ORDER_ACCEPTANCE"       [c]
                              "PROVIDER_ORDER_REJECTION"        [c]
                              "PROVIDER_ORDER_CLOSURE"          [c]
                              "PROVIDER_ORDER_TRACKING_ID"      [u]
                              "PROVIDER_INFORMATION"            [u]
                              "PROVIDER_CONTEXT"                [r]
                              "AUTHENTICATOR_DEFINITION"        [c d]
                              "PROVIDER_POLICIES"               [r u d]
                              "USER"                            [r]
                              "GROUP"                           [c r]
                              "PROVIDER_OBJECT_ACL"             [c r u d]
                              "CATALOG_ITEM_ACL"                [c r u d]
                              "INGEST_MANAGEMENT_ACL"           [r u]
                              "DATA_QUALITY_SUMMARY_DEFINITION" [c u d]
                              "DATA_QUALITY_SUMMARY_ASSIGNMENT" [c d]
                              "PROVIDER_CALENDAR_EVENT"         [c u d]}
   :system-identity          {"SYSTEM_AUDIT_REPORT"             [r]
                              "METRIC_DATA_POINT_SAMPLE"        [r]
                              "SYSTEM_INITIALIZER"              [c]
                              "ARCHIVE_RECORD"                  [d]
                              "ERROR_MESSAGE"                   [u]
                              "TOKEN"                           [r d]
                              "TOKEN_REVOCATION"                [c]
                              "EXTENDED_SERVICE_ACTIVATION"     [c]
                              "ORDER_AND_ORDER_ITEMS"           [r d]
                              "PROVIDER"                        [c d]
                              "TAG_GROUP"                       [c u d]
                              "TAXONOMY"                        [c]
                              "TAXONOMY_ENTRY"                  [c]
                              "USER_CONTEXT"                    [r]
                              "USER"                            [r u d]
                              "GROUP"                           [c r]
                              "ANY_ACL"                         [c r u d]
                              "EVENT_NOTIFICATION"              [d]
                              "EXTENDED_SERVICE"                [d]
                              "SYSTEM_OPTION_DEFINITION"        [c d]
                              "SYSTEM_OPTION_DEFINITION_DEPRECATION" [c]
                              "INGEST_MANAGEMENT_ACL"                [r u]
                              "SYSTEM_CALENDAR_EVENT"                [c u d]}})

(comment
  ;; evaluate the following expression to generate Markdown for the API docs
  (doseq [[identity-type targets-permissions] (sort-by key grantable-permission-mapping)]
    (println "####" identity-type)
    (println)
    (println "| Target | Allowed Permissions |")
    (println "| ------ | ------------------- |")
    (doseq [[target permissions] (sort-by key targets-permissions)]
      (println "|" target "|" (clojure.string/join ", " permissions) "|"))
    (println)))

(defn- get-identity-type
  [acl]
  (cond
    (:single-instance-identity acl) :single-instance-identity
    (:provider-identity acl)        :provider-identity
    (:system-identity acl)          :system-identity
    (:catalog-item-identity acl)    :catalog-item-identity))

(defn make-single-instance-identity-target-id-validation
  "Validates that the acl group exists."
  [context]
  (fn [key-path target-id]
    (when-not (groups/group-exists? context target-id)
      {key-path [(format "Group with concept-id [%s] does not exist" target-id)]})))

(defn- make-single-instance-identity-validations
  "Returns a standard validation for an ACL single_instance_identity field closed over the given context and ACL to be validated."
  [context]
  {:target-id (v/when-present (make-single-instance-identity-target-id-validation context))})

(defn permissions-granted-by-provider-to-user
  "Returns permissions granted for sids by the list of ACLs"
  [sids acls target]
  (for [sid sids
        acl acls
        :when (= (get-in acl [:provider-identity :target]) target)
        group-permission (:group-permissions acl)
        :when (or
                (and (contains? group-permission :user-type) (= (name sid) (name (:user-type group-permission))))
                (and (contains? group-permission :group-id) (= sid (:group-id group-permission))))
        permission (:permissions group-permission)]
    permission))

(defn get-acls-by-condition
  "Returns user in context, sids of user, and search items returned using condition"
  [context condition]
  (let [token (:token context)
        user (if token (tokens/get-user-id context token) "guest")
        sids (auth-util/get-sids context user)
        query (qm/query {:concept-type :acl
                         :condition condition
                         :skip-acls? true
                         :page-size :unlimited
                         :result-format :query-specified
                         :result-fields [:acl-gzip-b64]})
        response (qe/execute-query context query)
        response-acls (map #(edn/read-string (util/gzip-base64->string (:acl-gzip-b64 %))) (:items response))]
    {:items response-acls :sids sids :user user}))

(defn validate-target-provider-grants-create
  "Checks if provider ACL grants permission for user to create given catalog item ACL"
  [context key-path acl]
  (let [provider-id (:provider-id acl)
        condition (qm/string-condition :provider provider-id)
        response (get-acls-by-condition context condition)]
    (when-not (contains? (set (permissions-granted-by-provider-to-user (:sids response)
                                                                       (:items response)
                                                                       "CATALOG_ITEM_ACL"))
                         "create")
      {key-path [(format "User [%s] does not have permission to create a catalog item for provider-id [%s]"
                          (:user response) provider-id)]})))

(defn- make-catalog-item-identity-validations
  "Returns a standard validation for an ACL catalog_item_identity field
   closed over the given context and ACL to be validated.
   Takes action flag (:create or :update) to do different valiations
   based on whether creating or updating acl concept"
  [context acl action]
  (let [validations [catalog-item-identity-collection-or-granule-validation
                     catalog-item-identity-collection-applicable-validation
                     catalog-item-identity-granule-applicable-validation
                     {:collection-identifier (v/when-present (make-collection-identifier-validation context acl))
                      :granule-identifier (v/when-present granule-identifier-validation)}]]
    (if (= :create action)
      (merge validations #(validate-target-provider-grants-create context %1 %2))
      validations)))

(defn system-identity-create-permission-validation
  "Checks if user has permissions to create system level ACLs."
  [context key-path system-identity]
  (let [condition (qm/string-condition :identity-type "System" true false)
        response (get-acls-by-condition context condition)
        any-acl-system-acl (acl/echo-style-acl
                             (first (filter #(= "ANY_ACL" (:target (:system-identity %))) (:items response))))]
    (when-not (or (transmit-config/echo-system-token? context)
                  (acl/acl-matches-sids-and-permission? (:sids response) "create" any-acl-system-acl))
      {key-path [(format "User [%s] does not have permission to create a system level ACL" (:user response))]})))

(defn make-system-identity-validations
  "Returns validations for ACLs with a System identity"
  [context action]
  (when (= :create action)
    [(fn [key-path system-identity]
       (system-identity-create-permission-validation context key-path system-identity))]))

(defn validate-provider-exists
  "Validates that the acl provider exists."
  [context key-path acl]
  (let [provider-id (acls/acl->provider-id acl)]
    (when (and provider-id
               (not (some #{provider-id} (map :provider-id (mdb/get-providers context)))))
      {key-path [(msg/provider-does-not-exist provider-id)]})))

(defn validate-grantable-permissions
  "Checks if permissions requested are grantable for given target."
  [key-path acl]
  (let [identity-type (get-identity-type acl)
        target (:target (get acl identity-type))
        permissions-requested (mapcat :permissions (:group-permissions acl))
        grantable-permissions (get-in grantable-permission-mapping [identity-type target])
        ungrantable-permissions (remove (set grantable-permissions) permissions-requested)]
    (when (and (seq ungrantable-permissions) (seq (set grantable-permissions)))
      {key-path [(format "[%s] ACL cannot have [%s] permission for target [%s], only [%s] are grantable"
                         (name identity-type) (clojure.string/join ", " ungrantable-permissions)
                         target (clojure.string/join ", " grantable-permissions))]})))

(defn- make-acl-validations
  "Returns a sequence of validations closed over the given context for validating ACL records.
   Takes action flag (:create or :update) to do different valiations based on whether creating or updating acl concept"
  [context acl action]
  [#(validate-provider-exists context %1 %2)
   {:catalog-item-identity (v/when-present (make-catalog-item-identity-validations context acl action))
    :single-instance-identity (v/when-present (make-single-instance-identity-validations context))
    :system-identity (v/when-present (make-system-identity-validations context action))}
   validate-grantable-permissions])

(defn validate-acl-save!
  "Throws service errors if ACL is invalid. Takes action flag (:create or :update) to do different valiations
   based on whether creating or updating acl concept"
  [context acl action]
  (v/validate! (make-acl-validations context acl action) acl))
