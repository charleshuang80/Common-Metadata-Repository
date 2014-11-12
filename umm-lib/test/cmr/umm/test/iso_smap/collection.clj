(ns cmr.umm.test.iso-smap.collection
  "Tests parsing and generating SMAP ISO Collection XML."
  (:require [clojure.test :refer :all]
            [cmr.common.test.test-check-ext :refer [defspec]]
            [clojure.test.check.properties :refer [for-all]]
            [clojure.test.check.generators :as gen]
            [clojure.string :as s]
            [clojure.java.io :as io]
            [cmr.common.joda-time]
            [cmr.common.date-time-parser :as p]
            [cmr.spatial.mbr :as mbr]
            [cmr.umm.test.generators.collection :as coll-gen]
            [cmr.umm.iso-smap.collection :as c]
            [cmr.umm.echo10.collection :as echo10-c]
            [cmr.umm.echo10.core :as echo10]
            [cmr.umm.collection :as umm-c]
            [cmr.umm.iso-smap.core :as iso]
            [cmr.umm.test.echo10.collection :as test-echo10]))

(defn- spatial-coverage->expected-parsed
  "Returns the expected parsed spatial-coverage for the given spatial-coverage"
  [spatial-coverage]
  (let [{:keys [geometries]} spatial-coverage
        bounding-boxes (filter #(= cmr.spatial.mbr.Mbr (type %)) geometries)]
    (when (seq bounding-boxes)
      (umm-c/map->SpatialCoverage
        {:granule-spatial-representation :cartesian
         :spatial-representation :cartesian
         :geometries bounding-boxes}))))

(defn- umm->expected-parsed-smap-iso
  "Modifies the UMM record for testing SMAP ISO. ISO contains a subset of the total UMM fields
  so certain fields are removed for comparison of the parsed record"
  [coll]
  (let [{{:keys [short-name long-name version-id]} :product
         :keys [entry-title spatial-coverage associated-difs]} coll
        entry-id (str short-name "_" version-id)
        range-date-times (get-in coll [:temporal :range-date-times])
        single-date-times (get-in coll [:temporal :single-date-times])
        temporal (if (seq range-date-times)
                   (umm-c/map->Temporal {:range-date-times range-date-times
                                         :single-date-times []
                                         :periodic-date-times []})
                   (when (seq single-date-times)
                     (umm-c/map->Temporal {:range-date-times []
                                           :single-date-times single-date-times
                                           :periodic-date-times []})))
        organizations (seq (filter #(not (= :distribution-center (:type %))) (:organizations coll)))
        org-name (some :org-name organizations)
        contact-name (or org-name "undefined")
        associated-difs (when (first associated-difs) [(first associated-difs)])]
    (-> coll
        ;; SMAP ISO does not have entry-id and we generate it as concatenation of short-name and version-id
        (assoc :entry-id entry-id)
        ;; SMAP ISO does not have collection-data-type
        (assoc-in [:product :collection-data-type] nil)
        ;; SMAP ISO does not have processing-level-id
        (assoc-in [:product :processing-level-id] nil)
        ;; There is no delete-time in SMAP ISO
        (assoc-in [:data-provider-timestamps :delete-time] nil)
        ;; SMAP ISO does not have periodic-date-times
        (assoc :temporal temporal)
        ;; SMAP ISO does not have distribution centers as Organization
        (assoc :organizations organizations)
        ;; SMAP ISO only has one dif-id
        (assoc :associated-difs associated-difs)
        ;; SMAP ISO spatial only has BoundingBox
        (update-in [:spatial-coverage] spatial-coverage->expected-parsed)
        ;; SMAP ISO does not support RestrictionFlag
        (dissoc :access-value)
        ;; SMAP ISO does not support SpatialKeywords
        (dissoc :spatial-keywords)
        ;; SMAP ISO does not support TemporalKeywords
        (dissoc :temporal-keywords)
        ;; SMAP ISO does not support ScienceKeywords
        (dissoc :science-keywords)
        ;; SMAP ISO does not support Platforms
        (dissoc :platforms)
        ;; SMAP ISO does not support Projects
        (dissoc :projects)
        ;; SMAP ISO does not support AdditionalAttributes
        (dissoc :product-specific-attributes)
        ;; SMAP ISO does not support RelatedURLs
        (dissoc :related-urls)
        ;; SMAP ISO does not support two-d-coordinate-systems
        (dissoc :two-d-coordinate-systems)
        ;; We don't use these two fields during xml generation as they are not needed for ISO
        ;; so we set them to the defaults here.
        (assoc :contact-email "support@earthdata.nasa.gov")
        (assoc :contact-name contact-name)
        umm-c/map->UmmCollection)))

(defspec generate-collection-is-valid-xml-test 100
  (for-all [collection coll-gen/collections]
    (let [xml (iso/umm->iso-smap-xml collection)]
      (and
        (> (count xml) 0)
        (= 0 (count (c/validate-xml xml)))))))

(defspec generate-and-parse-collection-test 100
  (for-all [collection coll-gen/collections]
    (let [xml (iso/umm->iso-smap-xml collection)
          parsed (c/parse-collection xml)
          expected-parsed (umm->expected-parsed-smap-iso collection)]
      (= parsed expected-parsed))))

(defspec generate-and-parse-collection-between-formats-test 100
  (for-all [collection coll-gen/collections]
    (let [xml (iso/umm->iso-smap-xml collection)
          parsed-iso (c/parse-collection xml)
          echo10-xml (echo10/umm->echo10-xml parsed-iso)
          parsed-echo10 (echo10-c/parse-collection echo10-xml)
          expected-parsed (test-echo10/umm->expected-parsed-echo10 (umm->expected-parsed-smap-iso collection))]
      (and (= parsed-echo10 expected-parsed)
           (= 0 (count (echo10-c/validate-xml echo10-xml)))))))

(comment

  (let [x #cmr.umm.collection.UmmCollection{:entry-id "0", :entry-title "0", :summary "0", :product #cmr.umm.collection.Product{:short-name "0", :long-name "0", :version-id "0", :processing-level-id nil, :collection-data-type nil}, :access-value nil, :data-provider-timestamps #cmr.umm.collection.DataProviderTimestamps{:insert-time #=(org.joda.time.DateTime. 0), :update-time #=(org.joda.time.DateTime. 0), :delete-time nil}, :spatial-keywords nil, :temporal-keywords nil, :temporal #cmr.umm.collection.Temporal{:time-type nil, :date-type nil, :temporal-range-type nil, :precision-of-seconds nil, :ends-at-present-flag nil, :range-date-times [#cmr.umm.collection.RangeDateTime{:beginning-date-time #=(org.joda.time.DateTime. 0), :ending-date-time nil}], :single-date-times [], :periodic-date-times []}, :science-keywords [#cmr.umm.collection.ScienceKeyword{:category "0", :topic "0", :term "0", :variable-level-1 "0", :variable-level-2 nil, :variable-level-3 nil, :detailed-variable nil}], :platforms nil, :product-specific-attributes nil, :projects nil, :two-d-coordinate-systems nil, :related-urls nil, :organizations (#cmr.umm.collection.Organization{:type :processing-center, :org-name "!"} #cmr.umm.collection.Organization{:type :archive-center, :org-name "\""} #cmr.umm.collection.Organization{:type :distribution-center, :org-name "!"}), :personnel nil, :spatial-coverage nil, :associated-difs nil, :contact-email "         !", :contact-name "0"}
        xml (iso/umm->iso-smap-xml x)
        parsed (c/parse-collection xml)
        expected-parsed (umm->expected-parsed-smap-iso x)]
    (println xml)
    (println parsed)
    (println expected-parsed))



  )

(def sample-collection-xml
  (slurp (io/file (io/resource "data/iso_smap/sample_smap_iso_collection.xml"))))

(deftest parse-collection-test
  (let [expected (umm-c/map->UmmCollection
                   {:entry-id "SPL1AA_002"
                    :entry-title "SMAP Collection Dataset ID"
                    :summary "Parsed high resolution and low resolution radar instrument telemetry with spacecraft position, attitude and antenna azimuth information as well as voltage and temperature sensor measurements converted from telemetry data numbers to engineering units."
                    :product (umm-c/map->Product
                               {:short-name "SPL1AA"
                                :long-name "SMAP Level 1A Parsed Radar Instrument Telemetry"
                                :version-id "002"})
                    :data-provider-timestamps (umm-c/map->DataProviderTimestamps
                                                {:insert-time (p/parse-datetime "2013-04-04T15:15:00Z")
                                                 :update-time (p/parse-datetime "2013-04-05T17:15:00Z")})
                    :temporal
                    (umm-c/map->Temporal
                      {:range-date-times
                       [(umm-c/map->RangeDateTime
                          {:beginning-date-time (p/parse-datetime "2014-10-31T00:00:00.000Z")
                           :ending-date-time (p/parse-datetime "2018-01-31T00:00:00.000Z")})]
                       :single-date-times
                       []
                       :periodic-date-times []})
                    :spatial-coverage (umm-c/map->SpatialCoverage
                                        {:granule-spatial-representation :cartesian
                                         :spatial-representation :cartesian
                                         :geometries [(mbr/mbr -180.0 87.0 180.0 -87.0)]})
                    :associated-difs ["A_DIF_ID"]
                    :organizations
                    [(umm-c/map->Organization
                       {:type :processing-center
                        :org-name "Jet Propulsion Laboratory"})
                     (umm-c/map->Organization
                       {:type :archive-center
                        :org-name "Alaska Satellite Facility"})]
                    :contact-email "support@earthdata.nasa.gov"
                    :contact-name "National Aeronautics and Space Administration (NASA)"})
        actual (c/parse-collection sample-collection-xml)]
    (is (= expected actual))))

(deftest validate-xml
  (testing "valid xml"
    (is (= 0 (count (c/validate-xml sample-collection-xml)))))
  (testing "invalid xml"
    (is (= [(str "Line 6 - cvc-complex-type.2.4.a: Invalid content was found "
                 "starting with element 'gmd:XXXX'. One of "
                 "'{\"http://www.isotc211.org/2005/gmd\":fileIdentifier, "
                 "\"http://www.isotc211.org/2005/gmd\":language, "
                 "\"http://www.isotc211.org/2005/gmd\":characterSet, "
                 "\"http://www.isotc211.org/2005/gmd\":parentIdentifier, "
                 "\"http://www.isotc211.org/2005/gmd\":hierarchyLevel, "
                 "\"http://www.isotc211.org/2005/gmd\":hierarchyLevelName, "
                 "\"http://www.isotc211.org/2005/gmd\":contact}' is expected.")]
           (c/validate-xml (s/replace sample-collection-xml "fileIdentifier" "XXXX"))))))

