(ns descryptors.util
  (:require [clojure.edn :as edn])
  (:import [java.util Base64]))



(defn encode-str [to-encode]
  (.encodeToString (Base64/getEncoder) (.getBytes to-encode)))


(defn decode-str [to-decode]
  (String. (.decode (Base64/getDecoder) to-decode)))



(defn encode-edn [data]
  (some->> data pr-str encode-str))


(defn decode-edn [data]
  (some->> data decode-str edn/read-string))


