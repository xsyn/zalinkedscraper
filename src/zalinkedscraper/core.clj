(ns zalinkedscraper.core
  (:require [net.cgrand.enlive-html :as html]
            [clojure-csv.core :as csv]
            [clojure.string :as string]
            [clojure.java.io :as io]))

;; Setup the base url with za. prefix
(def base-url "https://za.linkedin.com/")

(def test-url "https://www.linkedin.com/in/thopola-brunhilda-3936b849")

(defn char-range [start end]
  "Creates range of letters"
  (map char (range (int start) (inc (int end)))))

(defn people-list [url]
  (map #(string/join [url "directory/people-" %]) (char-range \a \z)))

(defn fetch-url [url]
  (html/html-resource (java.net.URL. url)))

;; - Doing a crawl through the directories

(defn get-directory [url]
  (html/select
   (fetch-url url) [:div.section.last :div.columns :ul :li :a]))

(defn get-link [li]
  "Get the link from the row"
  (get-in li [:attrs :href]))

(defn flat-map-directory [list]
  (flatten (map get-directory list)))

;; get-link-repeat url-list 2 -- url-list is people-list url
(defn get-link-repeat [url-list n]
  (if (zero? n) url-list
      (get-link-repeat
       (map get-link (flat-map-directory url-list))
       (dec n))))

;;
(   ;;map get-link
 ;;
 (  ;;flat-map-directory
  ( ;;map get-link (flat-map-directory (people-list url))
   )))


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
    (conj (hashmap :user user :title title :industry industry)
          detail)))

;; Write data to csv

(defn write-csv [path row-data]
  (let [columns [:user :title :industry :Current :Previous :Education]
        headers (map name columns)
        rows (mapv #(mapv % columns) row-data)]
    (with-open [file (io/writer path)]
      (csv/write-csv file (cons headers rows)))))


