(ns zalinkedscraper.core
  (:require [net.cgrand.enlive-html :as html]
            [clojure-csv.core :as csv]
            [clojure.string :as string]
            [clojure.java.io :as io]
            [clj-http.client :as client]
            [clj-http.conn-mgr :as conn-mgr]
            [cheshire.core :refer :all]
            [taoensso.timbre :as timbre
             :refer (log  trace  debug  info  warn  error  fatal  report
                          logf tracef debugf infof warnf errorf fatalf reportf
                          spy get-env log-env)]
            [taoensso.timbre.profiling :as profiling
             :refer (pspy pspy* profile defnp p p*)])
  (:use [slingshot.slingshot :only [throw+ try+]]))

;; TODO: Setup exception handling for 404's


;; Setup the base url with za. prefix
(def base-url "https://za.linkedin.com/")

(def test-url "https://www.linkedin.com/in/thopola-brunhilda-3936b849")

(defn char-range [start end]
  "Creates range of letters"
  (map char (range (int start) (inc (int end)))))

(defn people-list [url]
  (map #(string/join [url "directory/people-" %]) (char-range \a \z)))

(defn get-url [url]
  (try+
   (client/get url {:insecure true
                    :connection-manager
                    (conn-mgr/make-socks-proxied-conn-manager "localhost" 9050)
                    })
   (catch [:status 403] {:keys [request-time headers body]}
     (do
       (println (warn "403" request-time headers))
       nil))
   (catch [:status 404] {:keys [request-time headers body]}
     (do
       (println (warn "NOT Found 404" request-time headers body))
       nil))
   (catch Object _
     (error (:throwable &throw-context) "unexpected error")
     (throw+))))

(defn fetch-url [url]
  (let [check-url (get-url url)]
    (if (nil? check-url)
      nil
      (html/html-resource (java.io.StringReader.
                           (:body check-url))))))

;; - Doing a crawl through the directories

(defn get-directory [url]
  (let [check-url (fetch-url url)]
    (if (nil? check-url)
      nil
      (html/select
       (fetch-url url) [:div.section.last :div.columns :ul :li :a]))))

(defn get-link [li]
  "Get the link from the row"
  (get-in li [:attrs :href]))

(defn flat-map-directory [list]
  (remove nil? (flatten (map get-directory list))))


;; Some validation functions for directory urls that go through to
;; other links, and links that don't have proper pathing

(defn add-za [url]
  (string/join "" ["https://za.linkedin.com/" url]))

(defn localize-url [url]
  (if (re-find #"https://" url) url
      (add-za url)))

(defn pub-url? [url]
  (if (re-find #"/pub/dir/" url) true
      false))

(defn dd-is-za? [htm]
  (if (=  (first (:content (first (html/select htm [:dd])))) "South Africa")
    true
    false))

(defn extract-dd-href [htm]
  (get-in (first (html/select htm [:a])) [:attrs :href]))

(defn get-directory-urls [url]
  (let [dir-links (html/select (fetch-url url) [:div.professionals.section :ul.content :li])]
    (map extract-dd-href (filter dd-is-za?  dir-links))))

(defn validate-url [url]
  (let [lurl (localize-url url)]
    (if (pub-url? lurl) (get-directory-urls lurl)
        lurl)))

;; Build a directory of links, has the nasty side-effect of writing
;; them to disk

(defn get-link-repeat [url-list n]
  "Set at Level 3 to get full list of South African URLS"
  (if (zero? n)
    (let [validated-list (flatten (map validate-url url-list))]
      (println (info "Opening link-list for writing"))
      (with-open [w (clojure.java.io/writer "link-list")]
        (doseq [line validated-list]
          (.write w line)
          (.newLine w)))
      validated-list)
    (get-link-repeat
     (map get-link (flat-map-directory url-list))
     (dec n))))

;; - Scraping the actual page
(defn get-user-page [url]
  (let [checked-url (fetch-url url)]
    (if (nil? checked-url)
      (do (println (warn (string/join ["Empty URL string: " url]))) nil)
      (html/select checked-url [:div.profile-card.vcard]))))

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
    (println (info "Opening key-map for writing"))
    (spit "key-map" (info (string/join "," [user title industry])) :append true)))

;; Run the crawl
(defn run-crawl []
  (map get-user-details (flatten (map get-user-page (get-link-repeat (people-list base-url) 3)))))

;; Main funciton
(defn -main []
  (run-crawl))
