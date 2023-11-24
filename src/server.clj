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
  (dorun (map (partial send-message msg) (vals @streams))))

(defn receive-event
  [stream event]
  (match event
    [:join room nickname] (do (println "join")
                              (println (keys @streams))
                              @(s/put! stream {:event :history :data @messages}))
    [:say msg] (do (swap! messages conj msg)
                   @(s/put! stream {:event :broadcast-message :data msg}))
    :else :err))

(defn register-stream
  [s]
  (swap! stream-id-counter inc)
  (swap! streams assoc @stream-id-counter s)
  s)

(defn stream-handler
  []
  (fn [s info]
    (register-stream s)
    (s/map #(receive-event s %) s)))

(defn -main
  [& args]
  (println "Invoke server with" args)
  (tcp/start-server
   (fn [s info]
     ((stream-handler)
      (common/wrap-duplex-stream common/protocol s)
      info))
   {:port common/port}))

