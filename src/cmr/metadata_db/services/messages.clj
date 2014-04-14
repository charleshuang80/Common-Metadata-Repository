(ns cmr.metadata-db.services.messages
  (:require [clojure.string :as string]
            [cmr.common.services.errors :as errors]))

(defn data-error [error-type msg-fn & args]
  (errors/throw-service-error error-type (apply msg-fn args)))

(defn missing-concept-id [concept-type provider-id native-id]
  (format
    "Concept with concept-type [%s] provider-id [%s] native-id [%s] does not exist."
    concept-type
    provider-id
    native-id))

(defn concept-does-not-exist [concept-id]
  (format
    "Concept with concept-id [%s] does not exist."
    concept-id))

(defn concept-with-concept-id-and-rev-id-does-not-exist [concept-id revision-id]
  (format
    "Concept with concept-id [%s] and revision-id [%s] does not exist."
    concept-id revision-id))

(defn invalid-revision-id [expected-id received-id]
  (format
    "Expected revision-id of [%s] got [%s]"
    expected-id received-id))

(defn invalid-revision-id-unknown-expected [revision-id]
  (format
    "Invalid revison-id [%s]"
    revision-id))

(defn missing-concept-type []
  "Concept must include concept-type.")

(defn missing-provider-id []
  "Concept must include provider-id.")

(defn missing-native-id []
  "Concept must include native-id.")

(defn invalid-concept-id [concept-id provider-id concept-type]
  (format "Concept-id [%s] for concept does not match provider-id [%s] or concept-type [%s]."
          concept-id
          provider-id
          concept-type))

(defn concept-exists-with-differnt-id [concept-id concept-type provider-id native-id]
  (format
    "A concept with a differnt concept-id from %s already exists for concept-type [%s] provider-id [%s] and native-id [%s]"
    concept-id
    concept-type
    provider-id
    native-id))

(defn maximum-save-attempts-exceeded []
  "Reached limit of attempts to save concept - giving up.")

(defn provider-does-not-exist [provider-id]
  (format "Provider with provider-id [%s] does not exist."
          provider-id))

(defn providers-do-not-exist [provider-ids]
  (format "Providers with provider-ids [%s] do not exist."
          (string/join ", " provider-ids)))

(defn provider-exists [provider-id]
  (format "Provider [%s] already exists."
          provider-id))

(defn provider-id-empty [provider-id]
  (format "Provider ID cannot be empty"))

(defn provider-id-too-long [provider-id]
  (format "Provider ID [%s] exceeds ten characters"
          provider-id))

(defn invalid-provider-id [provider-id]
  (format "provider-id [%s] is invalid" provider-id))
