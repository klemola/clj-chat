(ns common
  (:require
   [clojure.edn :as edn]
   [gloss.core :as gloss]
   [gloss.io :as io]
   [manifold.stream :as s]))

;; Shared variables

(def port 10000)

;; Shared functions

  ;; This uses [Gloss](https://github.com/clj-commons/gloss), which is a library for defining byte
  ;; formats, which are automatically compiled into encoder and streaming decoders.
  ;;
  ;; Here, we define a simple protocol where each frame starts with a 32-bit integer describing
  ;; the length of the string which follows.  We assume the string is EDN-encoded, and so we
  ;; define a `pre-encoder` of `pr-str`, which will turn our arbitrary value into a string, and
  ;; a `post-decoder` of `clojure.edn/read-string`, which will transform our string into a data
  ;; structure.
(def protocol
  (gloss/compile-frame
   (gloss/finite-frame :uint32
                       (gloss/string :utf-8))
   pr-str
   edn/read-string))

  ;; This function takes a raw TCP **duplex stream** which represents bidirectional communication
  ;; via a single stream.  Messages from the remote endpoint can be consumed via `take!`, and
  ;; messages can be sent to the remote endpoint via `put!`.  It returns a duplex stream which
  ;; will take and emit arbitrary Clojure data, via the protocol we've just defined.
  ;;
  ;; First, we define a connection between `out` and the raw stream, which will take all the
  ;; messages from `out` and encode them before passing them onto the raw stream.
  ;;
  ;; Then, we `splice` together a separate sink and source, so that they can be presented as a
  ;; single duplex stream.  We've already defined our sink, which will encode all outgoing
  ;; messages.  We must combine that with a decoded view of the incoming stream, which is
  ;; accomplished via `gloss.io/decode-stream`.
(defn wrap-duplex-stream
  [protocol s]
  (let [out (s/stream)]
    (s/connect
     (s/map #(io/encode protocol %) out)
     s)

    (s/splice
     out
     (io/decode-stream s protocol))))
