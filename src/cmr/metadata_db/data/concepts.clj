(ns cmr.metadata-db.data.concepts
  "Defines a protocol for CRUD operations on concepts.")

(defprotocol ConceptsStore
  "Functions for saving and retrieving concepts"

  (generate-concept-id
    [db concept]
    "Create a concept-id for a given concept type and provider id.")

  (get-concept-id
    [db concept-type provider-id native-id]
    "Return a distinct identifier for the given arguments.")

  (get-concept
    [db concept-type provider-id concept-id revision-id]
    [db concept-type provider-id concept-id]
    "Gets a version of a concept with a given concept-id and revision-id. If the
    revision-id is not given or is nil then the latest revision is returned.")

  (get-concept-by-provider-id-native-id-concept-type
    [db concept]
    "Gets a version of a concept that has the same concept-type, provider-id, and native-id
    as the given concept.")

  (get-concepts
    [db concept-type provider-id concept-id-revision-id-tuples]
    "Get a sequence of concepts by specifying a list of
    tuples holding concept-id/revision-id")

  (find-concepts
    [db params]
    "Finds concepts by the given parameters")

  (save-concept
    [db concept]
    "Saves a concept and returns the revision id. If the concept already
    exists then a new revision will be created. If a revision-id is
    included and it is not valid, e.g. the revision already exists,
    then an exception is thrown.")

  (force-delete
    [db concept-type provider-id concept-id revision-id]
    "Remove a revision of a concept from the database completely.")

  (force-delete-by-params
    [db params]
    "Deletes concepts by the given parameters")

  (reset
    [db]
    "Resets concept related data back to an initial fresh state. WARNING: For dev use only.")

  (get-expired-concepts
    [db provider concept-type]
    "Returns concepts that have a delete-time before now and have not been deleted
    for the given provider and concept-type."))













