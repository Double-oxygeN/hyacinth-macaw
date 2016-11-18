(ns hyacinth-macaw.uds
  (:import (org.newsclub.net.unix AFUNIXSocket AFUNIXSocketAddress)
           (java.io PrintWriter))
  (:require [clojure.java.io :as io]
            [clojure.data.json :as json]
            [clojure.core.async :refer [<! go-loop]]))

(def budgie-socket-file "/var/tmp/budgerigar.socket")
(def ^:private socket
  (let [client (AFUNIXSocket/newInstance)]
    (->> budgie-socket-file io/file AFUNIXSocketAddress. (.connect client))
    client))

(defn sender-channel [c]
  (let [out (-> socket .getOutputStream (PrintWriter. true))]
    (go-loop [data (<! c)]
      (->> (json/write-str {:color [29 161 242] :body data})
        (.println out))
      (recur (<! c)))))
