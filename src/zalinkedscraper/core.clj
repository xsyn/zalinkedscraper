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

;; Build a directory of links, has the nasty side-effect of writing
;; them to disk
(defn get-link-repeat [url-list n]
  "Set at Level 3 to get full list of South African URLS"
  (if (zero? n) (do
                  (with-open [w (clojure.java.io/writer "link-list")]
                    (doseq [line url-list]
                      (.write w line)
                      (.newLine w))))
      url-list
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

;; TODO: The below doesn't work due to the fact that the url's that
;; we're getting are redirects. Need to feed the redirected url into
;; get-user-page

;; e.g
;; https://za.linkedin.com/pub/a-v-connversions-chantal-edwards/39/4b4/568
;; redirects to:
;; https://www.linkedin.com/in/a-v-connversions-chantal-edwards-5684b439
;;
;; Maybe we're lucky, it looks like that url just parses

;; We need two function, 1 that changes za.linkedin to www.linkedin
;; form, one that redirect /pub/dir/url links to another path
;; and a main one goes through the list and decides which to use

(defn za-url-to-standard [za-url]
  "This very ugly function is to clean up the links received from the za directories, and change them to public links. TODO - clean this up, it's ugly"
  (let [urlv (string/split (string/replace za-url #"za.linkedin.com/pub/" "www.linkedin.com/in/") #"/")
        start-url (string/join "/" [(nth urlv 0)
                                    (nth urlv 1)
                                    (nth urlv 2)
                                    (nth urlv 3)
                                    (nth urlv 4)])
        end-url (string/join "" [(nth urlv 7)
                                 (nth urlv 6)
                                 (nth urlv 5)])]
    (string/join "-" [start-url end-url])))

(defn add-za [url]
  (string/join "" ["https://za.linkedin.com/" url]))

(defn fix-pub-dir [url]
  (if (re-find #"/pub/dir/" url) (add-za (string/replace url #"/pub/dir/" "/in/"))
      (add-za url)))

(defn validate-url [url]
  (if (re-find #"https://" url) (za-url-to-standard url)
      (fix-pub-dir url)))

;; Run the crawl
(defn run-crawl []
  (map get-user-details
       (map get-user-page
            (get-link-repeat
             (people-list base-url) 3))))

;; Save the crawl
(defn save-crawl [path]
  (write-csv path run-crawl))
