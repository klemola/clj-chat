(ns client
  "TCP chat client"
  (:require [aleph.tcp :as tcp]
            [clojure.core.match :refer [match]]
            [clojure.string :as string]
            [common]
            [manifold.deferred :as d]
            [manifold.stream :as s]))

(def help-text "\"h\": help, \"q\": quit, \"j <room name> <nickname>\": join room, \"s <message>\": send message to room")

(defn join-command
  [input]
  (let [[room-name nickname & rest] (string/split input #"\s+")]
    [:join room-name nickname]))

(defn input-command
  [input-str]
  (cond
    (re-matches #"^j\s+(.*)" input-str) (join-command (subs input-str 2))
    (re-matches #"^s\s+(.*)" input-str) [:say (subs input-str 2)]
    (= input-str "q") :quit
    (= input-str "h") :help
    :else :unknown))

(defn input
  []
  (println ">>")
  (let [value (read-line)]
    (input-command value)))

(defn display-message
  [msg]
  (println msg))

(defn server-response
  [response]
  (let [event (get response :event)
        data (get response :data)]
    (println "server-response" response)
    (match event
      :history (dorun (map display-message data))
      :broadcast-message (display-message data)
      :else nil)))

(defn send-event
  [client event]
  @(s/put! client event))

(defn consume-events [stream]
  (defn loop-fn []
    (let [message (s/take! stream)]
      (when message
        (server-response message)
        (recur))))

  (loop []
    (try
      (loop-fn)
      (catch Exception e
        (println "Error while consuming messages:" e)
        (recur)))))

(defn step
  [c command]
  (match command
    [:join _ _] (send-event c command)
    [:say _] (send-event c command)
    :help (println help-text)
    :unknown (println help-text)
    :quit nil)
  (input))

(defn chat-loop
  [input-result c]
  (if (= input-result :quit)
    (println "bye!")
    (recur (step c input-result) c)))

(defn client
  [host port]
  (d/chain (tcp/client {:host host, :port port})
           #(common/wrap-duplex-stream common/protocol %)))

(defn -main
  [& _]
  (let [c @(client "localhost" common/port)]
    (consume-events c)
    (chat-loop :help c)))
