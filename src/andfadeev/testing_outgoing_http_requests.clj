(ns andfadeev.testing-outgoing-http-requests
  (:require [clj-http.client :as http]
            [cheshire.core]
            [clojure.string :as str])
  (:gen-class))

(defn greet
  "Callable entry point to the application."
  [data]
  (println (str "Hello, " (or (:name data) "World") "!")))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (greet {:name (first args)}))

;; Some code to test

(defn- transform-abilities
  [{:keys [abilities] :as pokemon}]
  (let [abilities (->> abilities
                       (map (fn [ability]
                              (get-in ability [:ability :name])))
                       (into #{}))]
    (assoc pokemon :abilities abilities)))

(defn- transform-forms
  [{:keys [forms] :as pokemon}]
  (let [forms (->> forms
                   (map :name)
                   (into #{}))]
    (assoc pokemon :forms forms)))

(defn pokemon-by-id-url
  [base-url pokemon-id]
  (str/join
   "/"
   [base-url
    "api/v2/pokemon"
    pokemon-id]))

(defn get-pokemon-short-info
  [{:keys [base-url]} pokemon-id]
  (let [url (pokemon-by-id-url base-url pokemon-id)
        response (http/get url {:as :json
                                :throw-exceptions false})]
    (when-not (= 200 (:status response))
      (throw (ex-info "Failed to fetch pokemon details by id"
                      {:url url
                       :response response})))
    (-> (:body response)
        (transform-abilities)
        (transform-forms)
        (select-keys [:name :abilities :forms]))))

(comment
  (get-pokemon-short-info {:base-url "https://pokeapi.co"}
                          101))