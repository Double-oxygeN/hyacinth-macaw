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
            [hyacinth-macaw.uds :as uds]))

(def twitter-blue [29 161 242])

(defn- get-oauth-access-token [tw req pin]
  (if (empty? pin)
    (.getOAuthAccessToken tw req)
    (.getOAuthAccessToken tw req pin)))

(defn make-twitter
  []
  (let [twitter (-> (TwitterFactory.) .getInstance)
        stream (-> (TwitterStreamFactory.) .getInstance)
        desktop (Desktop/getDesktop)
        request-token (.getOAuthRequestToken twitter)]
    (print "Please login to Twitter and get the PIN code.\nPIN > ")
    (flush)
    (->> (.getAuthorizationURL request-token) URI. (.browse desktop))
    (let [line (read-line)]
      (try
        (doto (get-oauth-access-token twitter request-token line)
          (#(.setOAuthAccessToken twitter %))
          (#(.setOAuthAccessToken stream %)))
        (println "Succeeded in connection to your account.")
        {:twitter twitter :stream stream}
        (catch TwitterException e
          (println "Unable to get the access token."))))))

(defn- twitter-body [status from to mode]
  (let [format (SimpleDateFormat. "d/MMM/YYYY HH:mm:ss Z" Locale/JAPAN)]
    (case mode
      :tweet (str \@ (-> from .getScreenName) \[ (-> from .getName) \] \newline
                  (-> status .getText) \newline
                  \< (-> status .getId) \> \newline
                  (.format format (-> status .getCreatedAt)))
      :fav (str (-> from .getScreenName) \→ (-> to .getScreenName) \newline
                (-> status .getText) \newline
                (.format format (-> status .getCreatedAt)))
      "")))

(def listener
  (let [sender (chan)]
    (uds/sender-channel sender)
    (reify UserStreamListener
      (onStatus [this status]
        (println \< (-> status .getId) \>)
        (go (>! sender (json/write-str {:color {:body [240 240 240] :frame twitter-blue}
                                        :fade 255/900 :width 280 :height 210 :font-size 12
                                        :body (twitter-body status (.getUser status) nil :tweet)}))))
      (onDeletionNotice [this statusDeletionNotice] nil)
      (onTrackLimitationNotice [this numberOfLimitedStatuses] nil)
      (onScrubGeo [this userId upToStatusId] nil)
      (onStallWarning [this warning] this)
      (onFriendList [this friendIds] nil)
      (onFavorite [this source target favoritedStatus] nil
        (println "[Fav] <" (-> favoritedStatus .getId) \>)
        (go (>! sender (json/write-str {:color {:body [240 240 240] :frame [238 164 19]}
                                        :fade 255/600 :width 200 :height 150 :font-size 9
                                        :body (twitter-body favoritedStatus source target :fav)}))))
      (onUnfavorite [this source rarget favoritedStatus] nil)
      (onFavoritedRetweet [this source target favoritedRetweet] nil)
      (onRetweetedRetweet [this source target retweetedStatus] nil)
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

(defn substr [s start end]
  (if (integer? start)
    (if (integer? end)
      (subs s start end)
      (subs s start))))

(def ^:private help-text
  "
Hyacinth Macaw: Twitter Client for Budgerigar Bulletin
v0.2.0

commands  |description
----------+-------------------------
eval &x   |evaluate &x as Clojure
tw &x     |tweet &x
evtw &x   |eval &x and tweet
rep x &y  |reply &y to x (tweet id)
evrep x &y|eval &y and reply to x
fav x     |like x
rt x      |retweet x (tweet id)
favrt x   |fav x;rt x
show x    |show the tweet x
query x &y|search tweets from word x
          |opts:
          |  count n
          |  lang s
          |  since YYYY-MM-DD
          |  until YYYY-MM-DD
          |  sinceid n
help      |show this text
version   |show this version

")

(def ^:private version-text
  "
Hyacinth Macaw - v0.2.0
Twitter4j - v4.0.4
clojure - v1.8.0

")

(defn reply-tweet [t id msg]
  (.updateStatus t (-> msg StatusUpdate. (.inReplyToStatusId id))))

(defn- do-if-contains [mp & opts]
  (doall (map #(if (contains? mp (first %)) ((second %) ((first %) mp))) (partition 2 opts))))

(defn do-query [t word opts-map]
  (let [q (Query. word)]
    (do-if-contains opts-map
      :count #(.setCount q (Integer/parseInt %))
      :lang #(.setLang q %)
      :since #(.setSince q %)
      :sinceid #(.setSinceId q (Long/parseLong %))
      :until #(.setUntil q %))
    (-> t (.search q) .getTweets vec)))

(defn- seq-to-hashmap [coll]
  (->> coll (partition 2) (map #(vector (keyword (first %)) (second %))) flatten (apply hash-map)))

(defn command-twitter-action [t]
  (loop [line (-> (read-line) (str/split #";\s*"))]
    (let [commands (-> (first line) (str/split #"\s"))]
      (try
        (case (first commands)
          "eval" (->> commands rest (str/join \space) read-string eval str (println "[eval]"))
          "tw" (->> commands rest (str/join \space) (.updateStatus t))
          "evtw" (->> commands rest (str/join \space) read-string eval str (.updateStatus t))
          "rep" (->> commands (drop 2) (str/join \space) (reply-tweet t (-> commands second Long/parseLong)))
          "evrep" (->> commands (drop 2) (str/join \space) read-string eval str (reply-tweet t (-> commands second Long/parseLong)))
          "fav" (->> commands second Long/parseLong (.createFavorite t))
          "rt" (->> commands second Long/parseLong (.retweetStatus t))
          "favrt" (let [id (->> commands second Long/parseLong)] (.createFavorite t id) (.retweetStatus t id))
          "show" (->> commands second Long/parseLong (.showStatus t) (#(twitter-body % (.getUser %) nil :tweet)) (println "[show]"))
          "query" (->> commands (drop 2) seq-to-hashmap (do-query t (second commands)) (map #(twitter-body % (.getUser %) nil :tweet)) (str/join \newline) (apply str) (#(println "[query]\n" % "\n[/query]")))
          "help" (println help-text)
          "version" (println version-text)
          nil)
        (catch Exception e (println "caught exception:" (.getMessage e)))
        (finally (Thread/sleep 3000)))
      (if (empty? (rest line))
        (recur (-> (read-line) (str/split #";\s*")))
        (recur (rest line))))))
