(ns zalinkedscraper.core
  (:require [net.cgrand.enlive-html :as html]
            [clojure-csv.core :as csv]
            [clojure.string :as string]))

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

;; The below two functions create a map of Letter and directory-link,
;; however because it's a pattern
(defn get-first-directory [url]
  (html/select (fetch-url url) [:div.directory :ol :li :a]))

(defn create-first-directory-map [url]
  (let [temp (get-first-directory url)]
    (zipmap (map html/text temp )
            (map #(vals (:attrs %)) temp))))

(defn get-directory [url]
  (html/select
   (fetch-url url) [:div.section.last :div.columns :ul :li :a]))

(defn get-link [li]
  "Get the link from the row"
  (get-in li [:attrs :href]))

(defn flat-map-directory [list]
  (flatten (map get-directory list)))

(defn get-link-repeat [x]
  (repeatedly 2 (map get-link (flat-map-directory (people-list url)))))
;; people-list will bring back a list of urls, I want to map each url
;; with a full function thread

;; Go into each thread and pick up the new list of urls


;; This is both tiers tier, this should create a list of all the links
;; for the next tier to scrape.

;;
(   ;;map get-link
 ;;
 (  ;;flat-map-directory
  ( ;;map get-link (flat-map-directory (people-list url))
   )))


;; - Scraping the actual page
(defn get-user-page [url]
  (html/select (fetch-url url) [:div.profile-card.vcard]))

(defn get-pg-content [pg path]
  (:content (first (html/select pg path))))

(defn get-user-details [pg]
  (let [user (get-pg-content pg [:div :div :div :h1])
        title (get-pg-content pg [:div :div :div :p])
        industry (get-pg-content pg [:div :div :div :dl :dd])]))
