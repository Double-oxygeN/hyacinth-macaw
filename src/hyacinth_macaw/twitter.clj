(ns hyacinth-macaw.twitter
  (:import (java.awt Desktop)
           (java.net URI)
           (twitter4j Twitter TwitterFactory TwitterStreamFactory Status TwitterException UserStreamListener)
           (twitter4j.auth AccessToken RequestToken)
           (twitter4j.conf ConfigurationBuilder))
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
  (case mode
    :tweet (str \@ (-> from .getScreenName) \[ (-> from .getName) \] \newline
                (-> status .getText) \newline
                \< (-> status .getId) \>)
    :fav (str (-> from .getScreenName) \→ (-> to .getScreenName) \newline
              (-> status .getText))
    ""))

(def listener
  (let [sender (chan)]
    (uds/sender-channel sender)
    (reify UserStreamListener
      (onStatus [this status]
        (println \< (-> status .getId) \>)
        (go (>! sender (json/write-str {:color {:body [240 240 240] :frame twitter-blue}
                                        :fade 1 :width 240 :height 180 :font-size 12
                                        :body (twitter-body status (.getUser status) nil :tweet)}))))
      (onDeletionNotice [this statusDeletionNotice] nil)
      (onTrackLimitationNotice [this numberOfLimitedStatuses] nil)
      (onScrubGeo [this userId upToStatusId] nil)
      (onStallWarning [this warning] this)
      (onFriendList [this friendIds] nil)
      (onFavorite [this source target favoritedStatus] nil
        (go (>! sender (json/write-str {:color {:body [240 240 240] :frame [238 164 19]}
                                        :fade 1.2 :width 160 :height 120 :font-size 10
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

(defn command-twitter-action [t]
  (loop [line (-> (read-line) (str/split #";\s*"))]
    (let [commands (-> (first line) (str/split #"\s"))]
      (case (first commands)
        "tw" (.updateStatus t (str/join \space (rest commands)))
        "fav" (.createFavorite t (Long/parseLong (second commands)))
        "rt" (.retweetStatus t (Long/parseLong (second commands)))
        "favrt" (let [id (Long/parseLong (second commands))] (.createFavorite t id) (.retweetStatus t id))
        "show" (-> (.showStatus t (Long/parseLong (second commands))) .getText (#(println "[show]" %)))
        nil)
      (if (empty? (rest line))
        (recur (-> (read-line) (str/split #";\s*")))
        (recur (rest line))))))
