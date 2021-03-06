(ns graphqlize.lacinia.eql
  (:require [inflections.core :as inf]
            [clojure.string :as string]
            [honeyeql.debug :refer [trace>>]]))

(defn- eql-root-attr-ns [namespaces selection-tree]
  (let [raw-namespaces   (map name namespaces)
        raw-entity-ident (-> (ffirst selection-tree)
                             namespace
                             inf/hyphenate)]
    (if-let [raw-ns (first (filter #(string/starts-with? raw-entity-ident %) raw-namespaces))]
      (let [x (string/replace-first raw-entity-ident (str raw-ns "-") "")]
        (if (= x raw-ns)
          x
          (str raw-ns "." x)))
      raw-entity-ident)))

#_(eql-root-attr-ns [:public] {:Language/languageId [nil]})
#_(eql-root-attr-ns [:person] {:PersonStateProvince/languageId [nil]})

(def ^:private reserved-args #{:limit :offset :orderBy})

(defn- ident [root-attr-ns args]
  (->> (remove (fn [[k _]]
                 (reserved-args k)) args)
       (mapcat (fn [[k v]]
                 [(keyword root-attr-ns (inf/hyphenate (name k))) v]))
       vec))

#_(ident "language" {:first 1})
#_(ident "language" {:language-id 1})
#_(ident "film-actor" {:film-id  1
                       :actor-id 1})

(defn- eqlify-order-by-param [selection-tree param]
  (map (fn [[k v]]
         (let [root-ns (-> (ffirst selection-tree)
                           namespace
                           inf/hyphenate)]
           [(->> (name k)
                inf/hyphenate
                (keyword root-ns))
           (-> (name v)
               string/lower-case
               keyword)])) param))

#_(eqlify-order-by-param {:City/city [nil]}
                         {:firstName :ASC
                          :lastName  :DESC})

(defn- to-eql-param [selection-tree [arg value]]
  (case arg
    :orderBy [:order-by (eqlify-order-by-param selection-tree value)]
    [arg value]))

(defn- parameters [selection-tree args]
  (->> (filter (fn [[k _]]
                 (reserved-args k)) args)
       (map #(to-eql-param selection-tree %))
       (into {})))

(declare properties)

(defn- field->prop [namespaces selection-tree field]
  (let [root-attr-ns (eql-root-attr-ns namespaces selection-tree)
        prop         (->> (name field)
                          inf/hyphenate
                          (keyword root-attr-ns))]
    (if-let [{:keys [selections args]} (first (selection-tree field))]
      (let [parameters (parameters selections args)
            prop       (if (empty? parameters)
                         prop
                         (list prop parameters))]
        {prop (properties namespaces selections)})
      prop)))

(defn- properties [namespaces selection-tree]
  (vec (map #(field->prop namespaces selection-tree %) (keys selection-tree))))

(defn generate [namespaces selection-tree args]
  (let [root-attr-ns (eql-root-attr-ns namespaces selection-tree)
        ident        (ident root-attr-ns args)
        parameters   (parameters selection-tree args)
        ident        (if (empty? parameters)
                       ident
                       (list ident parameters))
        properties   (properties namespaces selection-tree)
        eql          [{ident properties}]]
    (trace>> :eql eql)))