(ns andfadeev.testing-outgoing-http-requests-test
  (:require [clj-http.client :as http]
            [clojure.test :refer :all]
            [andfadeev.testing-outgoing-http-requests :as core]
            [clj-http.fake :as clj-http-fake]
            [cheshire.core :as json]
            [clj-test-containers.core :as tc])
  (:import (clojure.lang ExceptionInfo)
           (com.github.tomakehurst.wiremock WireMockServer)
           (com.github.tomakehurst.wiremock.core WireMockConfiguration)))

(deftest test-with-clj-http-fake
  (let [base-url "http://fake-pokemon-api.com"
        pokemon-id 1]
    (testing "API returns 200"
      (clj-http-fake/with-fake-routes
        {(core/pokemon-by-id-url base-url pokemon-id)
         {:get (fn [_req]
                 {:status 200
                  :headers {}
                  :body (json/encode {:name "pokemon-name"
                                      :forms [{:name "form-a"}]
                                      :abilities [{:ability {:name "ability-a"}}
                                                  {:ability {:name "ability-b"}}]
                                      :some-other-field 123})})}}
        (is (= {:abilities #{"ability-a"
                             "ability-b"}
                :forms #{"form-a"}
                :name "pokemon-name"}
               (core/get-pokemon-short-info {:base-url base-url}
                                            pokemon-id)))))
    (testing "API returns 404"
      (clj-http-fake/with-fake-routes
        {(core/pokemon-by-id-url base-url pokemon-id)
         {:get (fn [_req]
                 {:status 404
                  :headers {}
                  :body ""})}}
        (is (thrown-with-msg?
             ExceptionInfo
             #"Failed to fetch pokemon details by id"
             (core/get-pokemon-short-info
              {:base-url base-url}
              pokemon-id)))))))

;; Couple issues with this approach:
;; 1. We are not doing a real HTTP call, so checking less
;; 2. We are now really coupled to HTTP library as we are mocking the internals, so it will be much harder to swap HTTP client, e.g. Apache HTTP to Java Native HTTP client

;; Second approach is to use Wiremock (or other similar tool of your preference)
;; Now we will execute real HTTP calls in tests, so we can easily swap HTTP clients when we need

(deftest test-with-wiremock-in-memory
  (let [wiremock-server (new WireMockServer
                             (.port (WireMockConfiguration/options) 8081))]
    (try
      (.start wiremock-server)
      (let [base-url (.baseUrl wiremock-server)
            pokemon-id 1]
        ;; Registering Stub in Wiremock
        (http/post
         (str base-url "/__admin/mappings")
         {:body
          (json/encode {:request
                        {:method "GET"
                         :url (str "/api/v2/pokemon/" pokemon-id)}
                        :response {:status 200
                                   :body (json/encode {:name "pokemon-name"
                                                       :forms [{:name "form-a"}]
                                                       :abilities [{:ability {:name "ability-a"}}
                                                                   {:ability {:name "ability-b"}}]
                                                       :some-other-field 123})
                                   :headers {:Content-Type "application/json"}}})})

        (testing "something"
          (is (= {:abilities #{"ability-a"
                               "ability-b"}
                  :forms #{"form-a"}
                  :name "pokemon-name"}
                 (core/get-pokemon-short-info {:base-url base-url}
                                              pokemon-id)))))
      (finally
        (.stop wiremock-server)))))

;; Benefits here is that you can avoid adding extra dependencies to your project
;; Everything you need to configure your Wiremock server is HTTP client
;; Especially useful if you already using TestContainers (e.g. for managing databases)
(deftest test-with-wiremock-test-containers
  (let [wiremock-container (-> (tc/create {:image-name "wiremock/wiremock:3.2.0"
                                           :exposed-ports [8080]})
                               (tc/start!))]
    (try
      (let [base-url (str "http://" (:host wiremock-container) ":"
                          (get (:mapped-ports wiremock-container) 8080))
            pokemon-id 1]
        ;; Registering Stub in Wiremock
        (http/post
         (str base-url "/__admin/mappings")
         {:body
          (json/encode {:request
                        {:method "GET"
                         :url (str "/api/v2/pokemon/" 1)}
                        :response {:status 200
                                   :body (json/encode {:name "pokemon-name"
                                                       :forms [{:name "form-a"}]
                                                       :abilities [{:ability {:name "ability-a"}}
                                                                   {:ability {:name "ability-b"}}]
                                                       :some-other-field 123})
                                   :headers {:Content-Type "application/json"}}})})

        (testing "something"
          (is (= {:abilities #{"ability-a"
                               "ability-b"}
                  :forms #{"form-a"}
                  :name "pokemon-name"}
                 (core/get-pokemon-short-info {:base-url base-url}
                                              pokemon-id)))))
      (finally
        (tc/stop! wiremock-container)))))
