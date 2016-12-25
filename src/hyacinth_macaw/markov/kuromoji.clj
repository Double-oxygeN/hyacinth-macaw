(ns hyacinth-macaw.markov.kuromoji
  (:import (com.atilika.kuromoji.ipadic Tokenizer)))

(defn build-tokenizer
  "Tokenizerクラスを返す"
  []
  (Tokenizer.))

(defn tokenize-str
  "解析する文字列を渡す"
  [tokenizer s]
  (.tokenize tokenizer s))

(defn get-surface
  "表層形(そのままの形)を得る"
  [token]
  (.getSurface token))

(defn get-all-features
  "語の特徴を全て返す"
  [token]
  (.getAllFeatures token))

(defn get-all-features-list
  "語の特徴を全てリストにして返す"
  [token]
  (-> token
    .getAllFeaturesArray
    list))

(defn get-reading
  "カナ表記を得る"
  [token]
  (.getReading token))

(defn get-base-form
  "原型を得る"
  [token]
  (.getBaseForm token))

(defn get-pronunciation
  "読みを得る"
  [token]
  (.getPronunciation token))

(defn get-conjugation-form
  "活用形を得る"
  [token]
  (.getConjugationForm token))

(defn get-conjugation-type
  "活用の種類を得る"
  [token]
  (.getConjugationType token))

(defn position
  "文中における語の位置を得る"
  [token]
  (.getPosition token))

(defn known?
  "既知の語であるかどうかを判定する"
  [token]
  (.isKnown token))
