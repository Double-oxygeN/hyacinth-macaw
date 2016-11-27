(ns hyacinth-macaw.twitter
  (:import (java.awt Desktop)
           (java.net URI)
           (java.util Locale)
           (java.lang Exception)
           (java.text SimpleDateFormat)
           (twitter4j Query StatusUpdate TwitterFactory TwitterStreamFactory TwitterException UserStreamListener))
  (:require [clojure.core.async :refer [>! chan go]]
            [clojure.data.json :as json]
            [clojure.string :as str]
            [hyacinth-macaw.uds :as uds]
            [hyacinth-macaw.conf :as conf]))

(def ^:private streaming-contents (atom []))
(def ^:private desktop (Desktop/getDesktop))

(defn make-twitter
  []
  (let [config (-> (conf/make-conf-builder) .build)
        twitter (-> config TwitterFactory. .getInstance)
        stream (-> config TwitterStreamFactory. .getInstance)
        request-token (.getOAuthRequestToken twitter)]
    (letfn [(get-oauth-access-token [tw req pin] (if (empty? pin) (.getOAuthAccessToken tw req) (.getOAuthAccessToken tw req pin)))
            (set-oauth-access-token [token tw] (.setOAuthAccessToken tw token))]
      (print "Please login to Twitter and get the PIN code.\nPIN > ")
      (flush)
      (->> (.getAuthorizationURL request-token) URI. (.browse desktop))
      (let [line (read-line)]
        (try
          (doto (get-oauth-access-token twitter request-token line)
            (set-oauth-access-token twitter)
            (set-oauth-access-token stream))
          (println "Succeeded in connection to your account.")
          {:twitter twitter :stream stream}
          (catch TwitterException e
            (println "Unable to get the access token.")))))))

(defn- twitter-body [status from to mode]
  (let [format (SimpleDateFormat. "d/MMM/YYYY HH:mm:ss Z" Locale/JAPAN)]
    (case mode
      :tweet (str \@ (-> from .getScreenName) \[ (-> from .getName) \] \newline
                  (-> status .getText) \newline
                  \< (-> status .getId) \> \newline
                  (.format format (-> status .getCreatedAt)))
      :from-to (str (-> from .getScreenName) \→ (-> to .getScreenName) \newline
                (-> status .getText) \newline
                (.format format (-> status .getCreatedAt)))
      "")))

(def ^:private listener
  (let [sender (chan)]
    (uds/sender-channel sender)
    (reify UserStreamListener
      (onStatus [this status]
        (swap! streaming-contents conj (str \< (-> status .getId) "> @" (-> status .getUser .getScreenName)))
        (go (>! sender (json/write-str {:color {:body [240 240 240] :frame [29 161 242]}
                                        :fade 255/900 :width 280 :height 210 :font-size 12
                                        :body (twitter-body status (.getUser status) nil :tweet)}))))
      (onDeletionNotice [this statusDeletionNotice] nil)
      (onTrackLimitationNotice [this numberOfLimitedStatuses] nil)
      (onScrubGeo [this userId upToStatusId] nil)
      (onStallWarning [this warning] this)
      (onFriendList [this friendIds] nil)
      (onFavorite [this source target favoritedStatus] nil
        (swap! streaming-contents conj (str "[Fav] <" (-> favoritedStatus .getId) "> @" (-> source .getScreenName)))
        (go (>! sender (json/write-str {:color {:body [240 240 240] :frame [242 161 29]}
                                        :fade 255/600 :width 200 :height 150 :font-size 9
                                        :body (twitter-body favoritedStatus source target :from-to)}))))
      (onUnfavorite [this source rarget favoritedStatus] nil)
      (onFavoritedRetweet [this source target favoritedRetweet] nil)
      (onRetweetedRetweet [this source target retweetedStatus] nil)
      (onQuotedTweet [this source target quotingTweet]
        (swap! streaming-contents conj (str "[Quote] <" (-> quotingTweet .getId) \>))
        (go (>! sender (json/write-str {:color {:body [240 240 240] :frame [161 242 29]}
                                        :fade 255/750 :width 200 :height 150 :font-size 9
                                        :body (twitter-body quotingTweet source target :from-to)}))))
      (onFollow [this source followedUser] nil)
      (onUnfollow [this source unfollowedUser] nil)
      (onDirectMessage [this directMessage] nil)
      (onUserListMemberAddition [this addedMember listOwner alist] nil)
      (onUserListSubscription [this subscriver listOwner alist] nil)
      (onUserListUnsubscription [this subscriber listOwner alist] nil)
      (onUserListCreation [this listOwner alist] nil)
      (onUserListUpdate [this listOwner alist] nil)
      (onUserListDeletion [this listOwner alist] nil)
      (onUserProfileUpdate [this updateUser] nil)
      (onUserSuspension [this user] nil)
      (onBlock [this source blockedUser] nil)
      (onUnblock [this source unblockedUser] nil)
      (onException [this ex] nil))))

(defn add-listener [t]
  (.addListener t listener))

(def ^:private help-text
  "
Hyacinth Macaw: Twitter Client for Budgerigar Bulletin
v0.2.0

commands  |description
----------+-----------------------------
eval &x   |evaluate &x as Clojure
tw &x     |tweet &x
evtw &x   |eval &x and tweet
rep x &y  |reply &y to x (tweet id)
evrep x &y|eval &y and reply to x
fav x     |like x
rt x      |retweet x (tweet id)
favrt x   |fav x;rt x
fetch     |fetch stream and show
show x    |show the tweet x
user @foo |show the tweets of user @foo
query x &y|search tweets from word x
          |opts:
          |  count n
          |  lang s
          |  since YYYY-MM-DD
          |  until YYYY-MM-DD
          |  sinceid n
browse x  |get URL from x and browse
pict x    |get picture from x and browse
help      |show this text
version   |show this version
exit      |quit this app
")

(def ^:private version-text
  (str "
Hyacinth Macaw - v0.2.0
Twitter4j - v4.0.4
clojure - v" (clojure-version) \newline))

(defn do-query [t word opts-map]
  (let [q (Query. word)]
    (letfn [(do-if-contains [mp & opts] (doall (map #(if (contains? mp (first %)) ((second %) ((first %) mp))) (partition 2 opts))))]
      (do-if-contains opts-map
        :count #(.setCount q (Integer/parseInt %))
        :lang #(.setLang q %)
        :since #(.setSince q %)
        :sinceid #(.setSinceId q (Long/parseLong %))
        :until #(.setUntil q %))
      (-> t (.search q) .getTweets vec))))

(defn console-color [s str-color bg-color]
  (let [esc "\033[" color-map (zipmap [:black :red :green :yellow :blue :magenta :cyan :white] (range 8))]
    (str esc \9 (get color-map str-color 9) ";4" (get color-map bg-color 9) \m s esc "0m")))

(defn command-twitter-action [t]
  (print "hyacinth macaw > ")
  (flush)
  (loop [line (-> (read-line) (str/split #";\s*"))]
    (let [commands (-> (first line) (str/split #"\s"))]
      (letfn [(seq-to-hashmap [coll] (->> coll (partition 2) (map #(vector (keyword (first %)) (second %))) flatten (apply hash-map)))
              (reply-tweet [t id msg] (.updateStatus t (-> msg StatusUpdate. (.inReplyToStatusId id))))
              (eval-str [s] (-> s read-string eval str))
              (get-second-id [comds] (-> comds second Long/parseLong))
              (kakoi [tag s] (str \[ tag \] \newline s \newline \[ \/ tag \]))]
        (try
          (case (first commands)
            "eval" (->> commands rest (str/join \space) eval-str (str "[eval]") (#(console-color % :white :blue)) println)
            "tw" (->> commands rest (str/join \space) (.updateStatus t))
            "evtw" (->> commands rest (str/join \space) eval-str (.updateStatus t))
            "rep" (->> commands (drop 2) (str/join \space) (reply-tweet t (-> commands second Long/parseLong)))
            "evrep" (->> commands (drop 2) (str/join \space) eval-str (reply-tweet t (-> commands second Long/parseLong)))
            "fav" (->> commands get-second-id (.createFavorite t))
            "rt" (->> commands get-second-id (.retweetStatus t))
            "favrt" (let [id (->> commands get-second-id)] (.createFavorite t id) (.retweetStatus t id))
            "fetch" (do (->> @streaming-contents (str/join \newline) (kakoi "fetch") (#(console-color % :green :black)) println) (reset! streaming-contents []))
            "show" (->> commands get-second-id (.showStatus t) (#(twitter-body % (.getUser %) nil :tweet)) (str "[show]") (#(console-color % :yellow :black)) println)
            "user" (->> commands second (.showUser t) .getId (.getUserTimeline t) vec (map #(twitter-body % (.getUser %) nil :tweet)) (str/join \newline) (apply str) (kakoi "user") (#(console-color % :cyan :black)) println)
            "query" (->> commands (drop 2) seq-to-hashmap (do-query t (second commands)) (map #(twitter-body % (.getUser %) nil :tweet)) (str/join \newline) (apply str) (kakoi "query") (#(console-color % :magenta :black)) println)
            "browse" (->> commands get-second-id (.showStatus t) .getURLEntities vec (map #(->> % .getExpandedURL URI. (.browse desktop))) doall)
            "pict" (->> commands get-second-id (.showStatus t) .getExtendedMediaEntities vec (map #(->> % .getMediaURLHttps URI. (.browse desktop))) doall)
            "help" (println help-text)
            "version" (println version-text)
            "exit" (do (println "cu!") (System/exit 0))
            "" nil
            (println (first commands) "- command not found"))
          (catch Exception e (do (println "caught exception:" (.getMessage e)) (Thread/sleep 3000))))
        (if (empty? (rest line))
          (do (print "hyacinth macaw > ") (flush) (recur (-> (read-line) (str/split #";\s*"))))
          (recur (rest line)))))))
