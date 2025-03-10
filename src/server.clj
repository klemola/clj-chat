(ns server
  "TCP chat server"
  (:require
   [clojure.core.match :refer [match]]
   [aleph.tcp :as tcp]
   [manifold.stream :as s]
   [common]))

(def messages (atom []))
(def stream-id-counter (atom 0))
(def streams (atom {}))

(defn send-message [msg stream]
  @(s/put! stream {:event :broadcast-message :data msg}))

(defn broadcast-message
  [msg]
  (doseq [stream (vals @streams)]
    (send-message msg stream)))

(defn receive-event
  [stream event]
  (println "Received event:" event)
  (match event
    [:join room nickname] (do
                            (println "Current streams:" (keys @streams))
                            @(s/put! stream {:event :history :data @messages}))
    [:say msg] (do
                 (swap! messages conj msg)
                 (broadcast-message msg))
    :else (println "Unknown event:" event)))

(defn register-stream
  [stream]
  (let [id (swap! stream-id-counter inc)]
    (swap! streams assoc id stream)
    (println "Registered stream:" id)
    stream))

(defn handle-client
  [stream info]
  (let [wrapped-stream (common/wrap-duplex-stream common/protocol stream)]
    (register-stream wrapped-stream)
    (s/consume #(receive-event wrapped-stream %) wrapped-stream)))

(defn -main
  [& args]
  (println "Starting server on port" common/port)
  (tcp/start-server handle-client {:port common/port})
  @(promise)) ; Keep the server running
