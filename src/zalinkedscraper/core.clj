(ns zalinkedscraper.core
  (:require [net.cgrand.enlive-html :as html]
            [clojure-csv.core :as csv]
            [clojure.string :as string]
            [clojure.java.io :as io]
            [clj-http.client :as client]
            [cheshire.core :refer :all]))

;; TODO: Moving over to clj-http and chesire

;; Setup the base url with za. prefix
(def base-url "https://za.linkedin.com/")

(def test-url "https://www.linkedin.com/in/thopola-brunhilda-3936b849")

(defn char-range [start end]
  "Creates range of letters"
  (map char (range (int start) (inc (int end)))))

(defn people-list [url]
  (map #(string/join [url "directory/people-" %]) (char-range \a \z)))

(defn fetch-url [url]
  (html/html-resource (java.io.StringReader.
                       (:body (parse-string
                               (client/get url {:insecure true}))))))

;; - Doing a crawl through the directories

(defn get-directory [url]
  (html/select
   (fetch-url url) [:div.section.last :div.columns :ul :li :a]))

(defn get-link [li]
  "Get the link from the row"
  (get-in li [:attrs :href]))

(defn flat-map-directory [list]
  (flatten (pmap get-directory list)))

(defn add-za [url]
  (string/join "" ["https://za.linkedin.com/" url]))

(defn validate-url [url]
  (if (re-find #"https://" url) url
      (add-za url)))

;; Build a directory of links, has the nasty side-effect of writing
;; them to disk

(defn get-link-repeat [url-list n]
  "Set at Level 3 to get full list of South African URLS"
  (if (zero? n) (do
                  (with-open [w (clojure.java.io/writer "link-list")]
                    (doseq [line url-list]
                      (.write w line)
                      (.newLine w)))
                  url-list)
      (get-link-repeat
       (map get-link (flat-map-directory url-list))
       (dec n))))

;; - Scraping the actual page
(defn get-user-page [url]
  (html/select (fetch-url url) [:div.profile-card.vcard]))

(defn get-pg-first-content [pg path]
  (first (:content (first (html/select pg path)))))

(defn create-detail-map [pg kpath vpath]
  (zipmap
   (map keyword (flatten (map :content (html/select pg kpath))))
   (flatten (map :content (html/select pg vpath)))))

;; This function is pretty ugly man
(defn get-user-details [pg]
  (let [user (get-pg-first-content pg [:div :div :div :h1])
        title (get-pg-first-content pg [:div :div :div :p])
        industry (get-pg-first-content pg [:div :div :div :dl :dd])
        detail (create-detail-map pg [:div :div :div :tr :th]
                                  [:div :div :div :tr :td :ol :li :a])]
    (conj (hash-map :user user :title title :industry industry)
          detail)
    ;; Creating side-effects for scraping
    (spit "key-map" (string/join "," [user title industry]) :append true)))

;; Run the crawl
(defn run-crawl []
  (map get-user-details (flatten (map get-user-page (get-link-repeat (people-list base-url) 3)))))

;; Save the crawl
(defn save-crawl [path]
  (write-csv path run-crawl))
