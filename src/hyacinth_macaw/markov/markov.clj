;; http://zehnpaard.hatenablog.com/entry/2016/05/11/211951
;; を参考にさせていただきました

(ns hyacinth-macaw.markov.markov
  (:require [hyacinth-macaw.markov.kuromoji :as k]))

(defn map-vals
  "map型のvalueそれぞれにfを施す"
  [f hmap]
  (zipmap (keys hmap) (map f (vals hmap))))

(defn reductions-vals
  "map型のvalueにreductionsを施す"
  [f hmap]
  (apply array-map
    (interleave
      (keys hmap)
      (reductions f (vals hmap)))))

(defn morph-tokenize
  "トークン化して形態素を返す"
  [s]
  (let [tokenizer (k/build-tokenizer)]
    (->> s
      (k/tokenize-str tokenizer)
      (pmap k/get-surface))))

(defn file->words
  "テキストファイルを単語に分解"
  [file]
  (-> file
      slurp
      morph-tokenize))

(defn make-ngrams
  "n単語ずつ取り出してリストにする"
  [words n]
  (->> words
       (iterate rest)
       (take n)
       (apply map vector)))

(defn cumulative-frequencies
  "頻度分析"
  [xs]
  (->> xs
       frequencies
       (reductions-vals +)))

(defn words->datamap [words n]
  (let [ngrams   (make-ngrams words n)
        n-1gram  (comp vec drop-last)
        grouped  (group-by n-1gram ngrams)]
    (->> grouped
         (map-vals #(map last %))
         (map-vals cumulative-frequencies))))

(defn next-word [starting-words data]
 (let [cum-freq  (get data starting-words)
       total     (second (last cum-freq))
       i         (rand-int total)
       pair-at-i (first
                   (filter #(< i (second %)) cum-freq))
       word-at-i (first pair-at-i)]
   word-at-i))

(defn markov-sequence [starting-words datamap]
 (letfn [(f [words]
            (conj (vec (rest words))
                  (next-word words datamap)))]
   (->> starting-words
        (iterate f)
        (map first))))

(defn combine-words
  "単語を繋げて文章にする"
  [words]
  (->> words
      ; (interpose " ")
      (apply str)))

(defn random-text
  "テキスト中からランダムにn文字の単語列をとって，word-count単語までのマルコフ連鎖を得る"
  [n word-count file]
  (let [whole-words (-> file file->words)
        total-words (count whole-words)
        words (->> whole-words (drop (rand-int (- total-words n))) (take n))
        datamap (-> whole-words (words->datamap (inc n)))]
    (if (contains? datamap words)
      (-> words
          (markov-sequence datamap)
          (#(take word-count %))
          combine-words))))
