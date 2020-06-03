(ns descryptors.mail
  (:require [taoensso.timbre :refer [info]]
            [integrant.core :as ig]
            [clojure.string :as string]
            [roll.google :as google]))



(defonce config nil)


(def mail-template
  (->>
   [ ;;"Content-Type: text/html;"
    "To: {{TO}}"
    ;;"From: {{FROM}}"
    "Subject: {{SUBJECT}}"
    "{{MESSAGE}}"]
   
   (interpose "\n")
   (apply str)))



(defmethod ig/init-key :descryptors/mail [_ opts]
  (alter-var-root #'config (constantly opts)))


(defmethod ig/halt-key! :descryptors/mail [_ _]
  (alter-var-root #'config (constantly nil)))




(defn send-message [{:keys [company first-name last-name email message]}]
  (when (not-empty message)
    (let [names (cond-> ""
                  first-name (str first-name " ")
                  last-name (str last-name)
                  :default not-empty)
        
          msg (cond-> ""
                message (str "\n" message "\n\n")
                email (str "Email: " email "\n")
                names (str "Name: " names "\n")
                company (str "Company: " company "\n"))]
      
      (-> mail-template
          (string/replace "{{TO}}" (:to config))
          (string/replace "{{SUBJECT}}" (str "message from " (or names email)))
          (string/replace "{{MESSAGE}}" msg)
          
          (google/send-message)))))




(comment

  (send-message {:company "Goodmorning Works"
                 :email "user@goodmorning.works"
                 :message "Hello. This\nis some message."})
  )
