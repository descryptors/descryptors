(ns descryptors.util
  (:require [goog.crypt.base64 :as b64]
            [clojure.edn :as edn]))



(defn location->sym []
  (->> (.. js/window -location -pathname)
       (re-find #"/(\w.*)")
       second))



(def encode (comp b64/encodeString pr-str))
(def decode (comp edn/read-string b64/decodeString))
