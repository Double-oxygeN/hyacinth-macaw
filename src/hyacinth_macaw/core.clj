(ns hyacinth-macaw.core
  (:require [hyacinth-macaw.twitter :as tw])
  (:gen-class))

(defn -main
  [& args]
  (let [client (tw/make-twitter)]
    (doto (:stream client) tw/add-listener .user)
    (tw/command-twitter-action (:twitter client))))
