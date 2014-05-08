(ns cmr.search.services.messages.orbit-number-messages
  "Contains messages for reporting responses to the user")

(defn invalid-orbit-number-msg
  []
  "orbit_number must be an number or a range in the form 'number,number'.")

(defn non-numeric-orbit-number-parameter
  []
  "orbit_number must be a number.")

(defn non-numeric-equator-crossing-longitude-parameter
  []
  "longitude must be a number between -180.0 and 180.0.")

(defn non-numeric-value-failed-validation
  []
  "Non-numeric valuue failed validation. This should have been rejected by parameter validation.")