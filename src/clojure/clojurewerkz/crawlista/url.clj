(ns clojurewerkz.crawlista.url
  (:import [java.net URI URL MalformedURLException]
           [clojurewerkz.urly UrlLike])
  (:use [clojure.string :only [split blank?]]
        [clojurewerkz.crawlista.string]
        [clojure.string :only [lower-case]]
        [clojurewerkz.urly.core :only [path-of url-like]]))

;;
;; Implementation
;;

;; ...

;;
;; API
;;

(defn strip-query-string
  [^String s]
  (.replaceAll s "\\?.*$" ""))

(def resourcify
  (comp (fn [^String s]
          (if-not (re-find #"\.([a-zA-Z0-9]+)$" (path-of s))
            (maybe-append s "/")
            s))
        strip-query-string
        lower-case))

(defn separate-query-string
  [^String s]
  (split s #"\?"))

(defn client-side-href?
  [^String s]
  (or (.startsWith s "#")
      (.startsWith s "(")
      (.startsWith (.toLowerCase s) "javascript")
      (blank? s)))

(defn crawlable-href?
  [^String s]
  (and (not (client-side-href? s)) (try
                                     (URI. (strip-query-string s))
                                     true
                                     (catch java.net.URISyntaxException se
                                       false)
                                     (catch Exception e
                                       false))))


(defprotocol URLNormalization
  (normalize-host [input] "Normalizes host by chopping off www. if necessary")
  (normalize-url  [input] "Normalizes URL by chopping off www. at the beginning and trailing slash at the end, if necessary")
  (absolutize     [input against] "Returns absolute URL")
  (relativize     [input against] "Returns relative URL"))

(extend-protocol URLNormalization
  String
  (normalize-host [input]
    (try
      (let [url  (URL. input)
            url* (URL. (.getProtocol url) (maybe-chopl (.toLowerCase (.getHost url)) "www.") (.getPort url) (.getFile url))]
        (str url*))
      (catch MalformedURLException e
        (maybe-chopl (.toLowerCase input) "www."))))
  (normalize-url [input]
    (maybe-chopr (normalize-host input) "/"))
  (absolutize [input against]
    (let [[input-without-query-string query-string] (separate-query-string input)
          against-without-last-path-segment         (-> (url-like (URI. against)) .withoutLastPathSegment .toURI)
          resolved                                  (.toString (.resolve against-without-last-path-segment (URI. input-without-query-string)))]
      (if query-string
        (str resolved "?" query-string)
        resolved)))

  URL
  (normalize-host [input]
    (URL. (.getProtocol input) (maybe-chopl (.toLowerCase (.getHost input)) "www.") (.getPort input) (.getFile input)))


  URI
  (normalize-host [input]
    (URI. (.getScheme input) nil (maybe-chopl (.toLowerCase (.getHost input)) "www.") (.getPort input) (.getPath input) nil nil))
  (absolutize [input ^java.net.URI against]
    (.resolve against input)))


(defprotocol DomainRoot
  (root? [input] "Returns true if given URL/URI is the site root"))

(extend-protocol DomainRoot
  String
  (root? [input]
    (root? (URI. (strip-query-string input))))

  URI
  (root? [input]
    (#{"" "/"} (UrlLike/pathOrDefault (.getPath input))))

  URL
  (root? [input]
    (root? (.toURI input))))


(defn- maybe-prepend-protocol
  "Fixes broken URLs like //jobs.arstechnica.com/list/1186 (that parse fine and both have host and are not considered absolute by java.net.URI)"
  ([^String uri-str]
     (maybe-prepend-protocol uri-str "http"))
  ([^String uri-str ^String proto]
     (let [uri (URI. uri-str)]
       (if (and (not (.isAbsolute uri))
                (not (nil? (.getHost uri))))
         (str proto ":" uri-str)
         uri-str))))

(defn local-to?
  [^String uri-str ^String host]
  (let [uri (URI. (-> uri-str strip-query-string (maybe-prepend-protocol "http")))]
    (or (and (.getHost uri)
             (= (maybe-prepend (.toLowerCase host) "www.") (maybe-prepend (.toLowerCase (.getHost uri)) "www.")))
        (not (.isAbsolute uri)))))
