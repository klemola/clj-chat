(ns server
  "TCP chat server"
  (:require
   [clojure.core.match :refer [match]]
   [aleph.tcp :as tcp]
   [manifold.stream :as s]
   [common]))

(def messages (atom []))

(defn reply
  [event]
  (match event
    [:join room nickname] (do (println "join") {:event :history :data @messages})
    [:say msg] (do (swap! messages conj msg) {:event :broadcast-message :data msg})
    :else :err))

;; This creates a handler which will apply `f` to any incoming message, and immediately
;; send back the result.  Notice that we are connecting `s` to itself, but since it is a duplex
;; stream this is simply an easy way to create an echo server.
(defn stream-handler
  [f]
  (fn [s info]
    (do (println info)
        (s/connect
         (s/map f s)
         s))))

(defn -main
  [& args]
  (println "Invoke server with" args)
  (tcp/start-server
   (fn [s info]
     ((stream-handler reply)
      (common/wrap-duplex-stream common/protocol s)
      info))
   {:port common/port}))

