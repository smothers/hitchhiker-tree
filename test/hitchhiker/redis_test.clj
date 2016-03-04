(ns hitchhiker.redis-test
  (:require [clojure.test :refer :all]
            [taoensso.carmine :as car :refer (wcar)]
            [hitchhiker.tree.core :as core]
            [hitchhiker.tree.messaging :as msg]
            [hitchhiker.tree.core-test]
            [hitchhiker.redis :as redis]
            [clojure.test.check :as tc]
            [clojure.test.check.clojure-test :refer (defspec)]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]))

(defn insert
  [t k]
  (msg/insert t k k))

(defn lookup-fwd-iter
  [t v]
  (seq (map first (msg/lookup-fwd-iter t v))))

(defn mixed-op-seq
  "This is like the basic mixed-op-seq tests, but it also mixes in flushes to redis
   and automatically deletes the old tree"
  [add-freq del-freq flush-freq universe-size num-ops]
  (prop/for-all [ops (gen/vector (gen/frequency
                                   [[add-freq (gen/tuple (gen/return :add)
                                                         (gen/no-shrink gen/int))]
                                    [flush-freq (gen/return [:flush])]
                                    [del-freq (gen/tuple (gen/return :del)
                                                         (gen/no-shrink gen/int))]])
                                 num-ops)]
                (assert (let [ks (wcar {} (car/keys "*"))]
                          (or (empty? ks)
                              (= ["refcount:expiry"] ks)))
                        "Start with no keys")
                (let [[b-tree root set]
                      (reduce (fn [[t root set] [op x]]
                                       (let [x-reduced (when x (mod x universe-size))]
                                         (condp = op
                                           :flush (let [t (:tree (core/flush-tree t (redis/->RedisBackend)))]
                                                    (when root
                                                      (wcar {} (redis/drop-ref root)))
                                                    #_(println "flush")
                                                    [t @(:storage-addr t) set])
                                           :add (do #_(println "add") [(insert t x-reduced) root (conj set x-reduced)])
                                           :del (do #_(println "del") [(msg/delete t x-reduced) root (disj set x-reduced)]))))
                                     [(core/b-tree (core/->Config 3 3 2)) nil #{}]
                                     ops)]
                  #_(println "Make it to the end of a test, tree has" (count (lookup-fwd-iter b-tree -1)) "keys left")
                  (let [res (= (lookup-fwd-iter b-tree -1) (sort set))]
                    (wcar {} (redis/drop-ref root))
                    (assert (= ["refcount:expiry"] (wcar {} (car/keys "*"))) "End with no keys")
                    res))))

(defspec test-many-keys-bigger-trees
  100
  (mixed-op-seq 800 200 10 1000 1000))

(comment
  (test-many-keys-bigger-trees)


  (count (remove  (reduce (fn [t [op x]]
            (let [x-reduced (when x (mod x 1000))]
              (condp = op
                :flush t
                :add (conj t x-reduced)
                :del (disj t x-reduced))))
          #{}
          (drop-last 2 opseq)) (lookup-fwd-iter (msg/delete test-tree -33) 0)))
  (:op-buf test-tree)
  (count (sort (reduce (fn [t [op x]]
            (let [x-reduced (when x (mod x 1000))]
              (condp = op
                :flush t
                :add (conj t x-reduced)
                :del (disj t x-reduced))))
          #{}
          opseq)))

(def opseq [[:add 37] [:del 40] [:add -3] [:add 9] [:add 23] [:add -5] [:del 9] [:add -37] [:add -22] [:del 19] [:add -9] [:del 11] [:add 23] [:del 4] [:add 1] [:add 26] [:add 16] [:add 34] [:add -32] [:add -12] [:add -30] [:add 27] [:add -35] [:del -11] [:add -6] [:add 28] [:add -32] [:add -2] [:del 15] [:del -22] [:add 8] [:add 0] [:add 30] [:add 36] [:add -7] [:add -2] [:add -34] [:add 5] [:add 6] [:del 38] [:add 34] [:add 24] [:add 20] [:add -23] [:del -34] [:add 0] [:add 27] [:add -10] [:del 4] [:add 38] [:del -40] [:del 40] [:add 9] [:add -35] [:del -7] [:add -32] [:add -38] [:del -5] [:add -22] [:add 18] [:add -2] [:add -9] [:add -26] [:add 16] [:add -3] [:add -6] [:del 18] [:add -6] [:add 40] [:del -36] [:del -35] [:del -17] [:add 32] [:del -30] [:add -37] [:add -36] [:add 10] [:del -15] [:del -6] [:del -22] [:add -21] [:del 32] [:add -9] [:flush] [:add 26] [:add -26] [:add -17] [:add 10] [:add 24] [:add -20] [:add 3] [:add -31] [:add -11] [:add -3] [:add 28] [:add -11] [:add -2] [:add -21] [:add -37] [:add 19] [:del -30] [:add 1] [:add 27] [:del 39] [:add -13] [:add -29] [:add -6] [:add -2] [:del -17] [:add -33] [:add -7] [:add -27] [:add -22] [:add 24] [:add -38] [:add -7] [:add 6] [:del -4] [:add 1] [:add -35] [:add 30] [:add -6] [:add -16] [:add 3] [:add -15] [:del -40] [:add 22] [:add -22] [:add -35] [:add 16] [:add 18] [:add 26] [:add 24] [:add 33] [:add -9] [:del -17] [:add -13] [:add 30] [:add 32] [:add 17] [:del 38] [:add -16] [:add 6] [:add 16] [:add 36] [:add 28] [:add -19] [:add 9] [:add 30] [:del 5] [:add 4] [:add -8] [:del 24] [:add 3] [:add 38] [:add 33] [:add -35] [:add -30] [:add 9] [:add -18] [:add 26] [:add -24] [:add -30] [:add -38] [:add -32] [:add -1] [:del -22] [:add 17] [:add 9] [:add -35] [:add 0] [:add -28] [:add -5] [:add -12] [:add -18] [:add 0] [:add -7] [:add -38] [:add 29] [:add 34] [:add 38] [:add 3] [:add 30] [:add -10] [:add -26] [:del 14] [:add 4] [:add -37] [:add -12] [:add 31] [:add 12] [:del 38] [:add -36] [:add -28] [:del -31] [:flush] [:add -11] [:del 28] [:add -18] [:add 6] [:add 0] [:del 16] [:add 21] [:add 22] [:add 39] [:add -9] [:add -13] [:add -26] [:add -35] [:del 16] [:add 31] [:add 18] [:add 0] [:add -2] [:add -34] [:add 23] [:del -16] [:add -17] [:add 3] [:del -1] [:del 13] [:del -6] [:add 1] [:del 39] [:add 37] [:add -9] [:add 19] [:del 7] [:add -38] [:add 21] [:del 2] [:add 12] [:add 9] [:add 15] [:add 27] [:add 39] [:del 26] [:add -24] [:add 19] [:add -38] [:add 8] [:del -28] [:add 37] [:add -9] [:add 2] [:add -12] [:add -2] [:del -18] [:add 3] [:add -31] [:del -18] [:add -38] [:add -23] [:add 32] [:add -20] [:add 12] [:add -17] [:add 33] [:add 29] [:add -16] [:add 37] [:add -6] [:add -24] [:add 22] [:add 15] [:add -24] [:add -20] [:add 14] [:add 6] [:add -2] [:add -39] [:add 15] [:add -38] [:add 24] [:del -10] [:add -35] [:add 24] [:add 26] [:del 5] [:add -3] [:add -3] [:add -13] [:add -10] [:add 23] [:del 33] [:del 36] [:add 11] [:add 7] [:add 28] [:del -37] [:del 22] [:add 33] [:add 19] [:add 12] [:add -25] [:add -21] [:flush] [:del -31] [:del -35] [:add -27] [:add 26] [:del -7] [:add -2] [:add -35] [:add 16] [:add 10] [:add -8] [:add 16] [:del 11] [:add 25] [:add -14] [:add 8] [:add -1] [:add 22] [:add -4] [:add 9] [:add 12] [:add -13] [:add 11] [:add -35] [:add -39] [:add 40] [:add -6] [:add 25] [:add 39] [:del 20] [:add -38] [:add 26] [:add 36] [:add -31] [:add 36] [:add 37] [:add 7] [:add -13] [:add -27] [:add -16] [:add -13] [:add 18] [:add -30] [:flush] [:add 34] [:add -20] [:add 40] [:add 3] [:add -8] [:add 12] [:add -14] [:add -5] [:del -21] [:add 40] [:add -5] [:add -12] [:add -9] [:add 0] [:add -7] [:del 11] [:add -35] [:add -36] [:add -30] [:add -37] [:add -15] [:add -28] [:del -16] [:add 36] [:del 22] [:add -8] [:del -30] [:add 24] [:del -6] [:add -1] [:add -22] [:del -29] [:add 16] [:add 13] [:add 36] [:del 40] [:add 15] [:add -25] [:add 19] [:add 9] [:del -22] [:add -6] [:del -8] [:del 17] [:add 4] [:add 15] [:add -10] [:add 26] [:add -36] [:add -15] [:add -20] [:del 35] [:del -5] [:add 17] [:add 2] [:add -36] [:add -28] [:add 8] [:add -38] [:add -17] [:add -24] [:add -13] [:add -20] [:add 29] [:add -9] [:add 15] [:add -36] [:del 14] [:add -15] [:add 9] [:add -14] [:add 12] [:add 8] [:add -36] [:add -27] [:add -36] [:del -40] [:del 24] [:del 6] [:add -11] [:add -2] [:add -21] [:add -32] [:add 9] [:add -5] [:add -23] [:add -33] [:add -19] [:add -30] [:add -10] [:add -21] [:add -22] [:add -10] [:del 4] [:add 6] [:add -11] [:add 3] [:del 26] [:add -18] [:add 5] [:add -11] [:add -21] [:add 0] [:add -12] [:add -40] [:add -8] [:add 11] [:add -7] [:add 21] [:add -32] [:add -5] [:add -38] [:add -14] [:add 30] [:add -3] [:del -6] [:flush] [:add -27] [:add 4] [:add 0] [:del -1] [:add 21] [:add -8] [:del -36] [:add 5] [:add 23] [:del 20] [:add -23] [:add 8] [:add 24] [:add -3] [:add -35] [:add -31] [:add -7] [:add 30] [:add 39] [:del -18] [:add 21] [:add 26] [:add -27] [:del 14] [:add 12] [:add 18] [:add 6] [:add -4] [:add -6] [:add -19] [:del -16] [:add -29] [:add -5] [:del 16] [:add 37] [:add 39] [:add -21] [:add 13] [:add 33] [:add -33] [:add 32] [:add -2] [:add -5] [:del 23] [:add -14] [:add -14] [:add -39] [:add 12] [:add -12] [:add 33] [:add 1] [:add 29] [:add 19] [:add 3] [:add 17] [:del 37] [:add -14] [:add 5] [:del -2] [:add -3] [:add 25] [:add -26] [:add 3] [:add 30] [:add 28] [:add -16] [:del 9] [:add 40] [:add 21] [:add 15] [:add 22] [:del -19] [:add -13] [:add 8] [:add 23] [:add -26] [:add 9] [:add 2] [:add -5] [:add 3] [:add 37] [:add 23] [:add -40] [:add 8] [:add -19] [:add 23] [:del -27] [:add 9] [:add 35] [:add -29] [:add -19] [:add -11] [:add -16] [:del 27] [:add -18] [:add 26] [:add 40] [:add 34] [:del 15] [:add 20] [:add 10] [:add 40] [:del -13] [:del 4] [:add -34] [:del -19] [:add -21] [:add 1] [:add 39] [:del -24] [:del 2] [:add -5] [:add -26] [:del 35] [:add 24] [:add -4] [:add -7] [:add -26] [:add 9] [:del 0] [:add 37] [:add 1] [:add -28] [:add 18] [:add 31] [:add 31] [:add 38] [:del 28] [:add -27] [:add 38] [:add -16] [:add 5] [:add 27] [:add 27] [:add -17] [:add -1] [:add 4] [:add -32] [:del -32] [:del -18] [:add 1] [:add -3] [:add -19] [:add -10] [:add 37] [:add -16] [:add 24] [:add -30] [:del 21] [:add 35] [:add -24] [:add 11] [:add -36] [:add -24] [:add -35] [:add -36] [:add 8] [:add 40] [:add -22] [:add 10] [:add 6] [:del -22] [:del -12] [:add 5] [:add 21] [:add 27] [:add 11] [:add -17] [:del 37] [:add -7] [:add 24] [:add 13] [:add -26] [:add 32] [:del 27] [:add -28] [:add -8] [:add 14] [:add -31] [:add -2] [:add -24] [:del 2] [:add -38] [:del 23] [:del -30] [:add 28] [:add -34] [:add 18] [:del -27] [:del 23] [:add -19] [:del 20] [:add -31] [:add 0] [:add 1] [:del -26] [:add 39] [:add 26] [:del 31] [:add 35] [:del -34] [:del 1] [:del 24] [:del 23] [:add -14] [:add -2] [:del -37] [:del 17] [:add -7] [:add 7] [:add 35] [:add 8] [:add -19] [:add -11] [:add -12] [:add -22] [:add 32] [:del 20] [:add 7] [:add -36] [:add -30] [:add 10] [:add -23] [:add 4] [:add 19] [:add -17] [:add 25] [:add 18] [:add -1] [:add -37] [:add -25] [:add -37] [:add -34] [:add 21] [:add -27] [:add -27] [:add -18] [:add -31] [:add 38] [:add -17] [:add -2] [:add -15] [:add 36] [:del -23] [:add 34] [:add -1] [:add -36] [:del 15] [:add 29] [:add 17] [:add -22] [:add 37] [:add 15] [:del -23] [:add 30] [:add -4] [:add 3] [:add 4] [:add -2] [:add 20] [:add -16] [:del 39] [:add -1] [:add 3] [:del -14] [:add -38] [:del -25] [:add 29] [:add -24] [:del 26] [:add 35] [:del 7] [:add -5] [:del 7] [:add 40] [:add -33] [:add 34] [:add 26] [:add 34] [:del 20] [:add 31] [:add 33] [:add 38] [:add 19] [:del 17] [:add 19] [:add -25] [:add -10] [:add -37] [:add 8] [:add -21] [:add 13] [:add 25] [:add 30] [:add 25] [:add -26] [:del -25] [:del -34] [:add 5] [:add -35] [:add 39] [:add -33] [:add 27] [:del 40] [:flush] [:add -34] [:add 10] [:del -21] [:add -8] [:add 22] [:add -4] [:add 38] [:add -17] [:add -34] [:add 34] [:add -20] [:add 30] [:del -1] [:add -3] [:del 7] [:add 21] [:del 24] [:add 17] [:del -38] [:add -2] [:del 9] [:del 11] [:add -2] [:add 3] [:add 35] [:add 8] [:del -6] [:add -37] [:add 17] [:del -35] [:add -32] [:add -13] [:add 7] [:add 21] [:del -18] [:del 34] [:add -26] [:del -27] [:add -21] [:add 4] [:add -36] [:del -4] [:add 9] [:del -21] [:add -15] [:add -4] [:del 4] [:add 0] [:add 15] [:add -30] [:del 12] [:del 33] [:add -22] [:add -19] [:add -22] [:add -12] [:add -2] [:flush] [:add -20] [:add -20] [:add -33] [:add -17] [:del 1] [:add -2] [:add 38] [:add -39] [:add 14] [:add -8] [:add -19] [:add 7] [:add -13] [:del 39] [:add 38] [:del -14] [:del -40] [:del -18] [:add 38] [:del 0] [:add 37] [:add -16] [:add -29] [:del 30] [:add 16] [:add -31] [:del -2] [:add 37] [:add -10] [:add -26] [:add 33] [:add -23] [:add -14] [:add -18] [:del 10] [:add 5] [:add -14] [:add -22] [:del 32] [:add -25] [:add -40] [:add -12] [:add -21] [:add -9] [:add -32] [:add 28] [:add -21] [:add 13] [:del -21] [:del 31] [:add 10] [:add -11] [:del -17] [:add -25] [:add 9] [:add 0] [:add -22] [:del 7] [:add -38] [:add 4] [:add -5] [:add -13] [:add 14] [:del 26] [:add 18] [:add -18] [:add 5] [:add 32] [:add 17] [:add -5] [:del -30] [:add -34] [:add -22] [:add -33] [:add -2] [:del 1] [:add 22] [:add 39] [:add 21] [:add -38] [:add 1] [:add 23] [:add 17] [:add 5] [:del -18] [:add -14] [:add 30] [:del 37] [:add 3] [:add -39] [:add -1] [:del 22] [:add 39] [:del -26] [:del -26] [:add -37] [:del 5] [:add 26] [:add -20] [:add -21] [:del 6] [:add -20] [:add 36] [:add 7] [:del 11] [:add -26] [:del 6] [:add -17] [:del -17] [:add -30] [:add 1] [:del -35] [:add 4] [:del -17] [:add -6] [:del 9] [:add 34] [:add -35] [:add 8] [:add -11] [:del -1] [:add 13] [:add 20] [:add -22] [:add 7] [:add -13] [:add -24] [:add -37] [:add 32] [:add -30] [:del -34] [:del -31] [:add 19] [:flush] [:add 38] [:add 9] [:add -29] [:add -32] [:del -23] [:del 8] [:del -6] [:add 17] [:add -34] [:add -15] [:del 13] [:add -40] [:add 37] [:add -18] [:add 33] [:add -9] [:add -23] [:add -37] [:add 2] [:add 16] [:add 20] [:add -8] [:add -28] [:add -27] [:add -20] [:add 20] [:add 26] [:add -38] [:add -2] [:add -21] [:add 17] [:add 9] [:del 32] [:del 1] [:add -4] [:add -3] [:del -9] [:add -21] [:add -31] [:add -19] [:add 12] [:add 14] [:add -33] [:del -33] [:del -9] [:add -22]])
(let [ops (->> (read-string (slurp "broken-data.edn"))
               (map (fn [[op x]] [op (mod x 100000)]))
               (drop-last  125))]
                  (let [[b-tree s] (reduce (fn [[t s] [op x]]
                                         (let [x-reduced (mod x 100000)]
                                           (condp = op
                                             :add [(insert t x-reduced)
                                                   (conj s x-reduced)]
                                             :del [(msg/delete t x-reduced)
                                                   (disj s x-reduced)])))
                                       [(core/b-tree (core/->Config 3 3 2)) #{}]
                                       ops)]
                     (println ops)
 (println (->> (read-string (slurp "broken-data.edn"))
               (map (fn [[op x]] [op (mod x 100000)]))
               (take-last  125)
               first)) 
                    (println (lookup-fwd-iter b-tree -1))
                    (println (sort s))
                    ))
(defn trial []
  (let [opseq (read-string (slurp "broken-data.edn"))
                     [b-tree root] (reduce (fn [[t root] [op x]]
                                       (let [x-reduced (when x (mod x 1000))]
                                         (condp = op
                                           :flush (let [_ (println "About to flush...")
                                                        t (:tree (core/flush-tree t (redis/->RedisBackend)))]
                                                    (when root
                                                      (wcar {} (redis/drop-ref root)))
                                                    (println "flushed")
                                                    [t @(:storage-addr t)])
                                           :add (do (println "about to add" x-reduced "...")
                                                    (let [x [(insert t x-reduced) root]]
                                                      (println "added") x
                                                      ))
                                           :del (do (println "about to del" x-reduced "...")
                                                    (let [x [(msg/delete t x-reduced) root]]
                                                      (println "deled") x)))))
                                     [(core/b-tree (core/->Config 3 3 2))]
                                     opseq)]
                 (def test-tree b-tree)
                 (println "Got diff"
                          (count (remove  (reduce (fn [t [op x]]
                                                    (let [x-reduced (when x (mod x 1000))]
                                                      (condp = op
                                                        :flush t
                                                        :add (conj t x-reduced)
                                                        :del (disj t x-reduced))))
                                                  #{}
                                                  opseq) (lookup-fwd-iter test-tree 0))))
  (println "balanced?" (hitchhiker.tree.core-test/check-node-is-balanced test-tree))
                 (def my-root root)))

  (map #(and (second %) (mod (second %) 1000)) opseq)
(37 40 997 9 23 995 9 963 978 19 991 11 23 4 1 26 16 34 968 988 970 27 965 989 994 28 968 998 15 978 8 0 30 36 993 998 966 5 6 38 34 24 20 977 966 0 27 990 4 38 960 40 9 965 993 968 962 995 978 18 998 991 974 16 997 994 18 994 40 964 965 983 32 970 963 964 10 985 994 978 979 32 991 nil 26 974 983 10 24 980 3 969 989 997 28 989 998 979 963 19 970 1 27 39 987 971 994 998 983 967 993 973 978 24 962 993 6 996 1 965 30 994 984 3 985 960 22 978 965 16 18 26 24 33 991 983 987 30 32 17 38 984 6 16 36 28 981 9 30 5 4 992 24 3 38 33 965 970 9 982 26 976 970 962 968 999 978 17 9 965 0 972 995 988 982 0 993 962 29 34 38 3 30 990 974 14 4 963 988 31 12 38 964 972 969 nil 989 28 982 6 0 16 21 22 39 991 987 974 965 16 31 18 0 998 966 23 984 983 3 999 13 994 1 39 37 991 19 7 962 21 2 12 9 15 27 39 26 976 19 962 8 972 37 991 2 988 998 982 3 969 982 962 977 32 980 12 983 33 29 984 37 994 976 22 15 976 980 14 6 998 961 15 962 24 990 965 24 26 5 997 997 987 990 23 33 36 11 7 28 963 22 33 19 12 975 979 nil 969 965 973 26 993 998 965 16 10 992 16 11 25 986 8 999 22 996 9 12 987 11 965 961 40 994 25 39 20 962 26 36 969 36 37 7 987 973 984 987 18 970 nil 34 980 40 3 992 12 986 995 979 40 995 988 991 0 993 11 965 964 970 963 985 972 984 36 22 992 970 24 994 999 978 971 16 13 36 40 15 975 19 9 978 994 992 17 4 15 990 26 964 985 980 35 995 17 2 964 972 8 962 983 976 987 980 29 991 15 964 14 985 9 986 12 8 964 973 964 960 24 6 989 998 979 968 9 995 977 967 981 970 990 979 978 990 4 6 989 3 26 982 5 989 979 0 988 960 992 11 993 21 968 995 962 986 30 997 994 nil 973 4 0 999 21 992 964 5 23 20 977 8 24 997 965 969 993 30 39 982 21 26 973 14 12 18 6 996 994 981 984 971 995 16 37 39 979 13 33 967 32 998 995 23 986 986 961 12 988 33 1 29 19 3 17 37 986 5 998 997 25 974 3 30 28 984 9 40 21 15 22 981 987 8 23 974 9 2 995 3 37 23 960 8 981 23 973 9 35 971 981 989 984 27 982 26 40 34 15 20 10 40 987 4 966 981 979 1 39 976 2 995 974 35 24 996 993 974 9 0 37 1 972 18 31 31 38 28 973 38 984 5 27 27 983 999 4 968 968 982 1 997 981 990 37 984 24 970 21 35 976 11 964 976 965 964 8 40 978 10 6 978 988 5 21 27 11 983 37 993 24 13 974 32 27 972 992 14 969 998 976 2 962 23 970 28 966 18 973 23 981 20 969 0 1 974 39 26 31 35 966 1 24 23 986 998 963 17 993 7 35 8 981 989 988 978 32 20 7 964 970 10 977 4 19 983 25 18 999 963 975 963 966 21 973 973 982 969 38 983 998 985 36 977 34 999 964 15 29 17 978 37 15 977 30 996 3 4 998 20 984 39 999 3 986 962 975 29 976 26 35 7 995 7 40 967 34 26 34 20 31 33 38 19 17 19 975 990 963 8 979 13 25 30 25 974 975 966 5 965 39 967 27 40 nil 966 10 979 992 22 996 38 983 966 34 980 30 999 997 7 21 24 17 962 998 9 11 998 3 35 8 994 963 17 965 968 987 7 21 982 34 974 973 979 4 964 996 9 979 985 996 4 0 15 970 12 33 978 981 978 988 998 nil 980 980 967 983 1 998 38 961 14 992 981 7 987 39 38 986 960 982 38 0 37 984 971 30 16 969 998 37 990 974 33 977 986 982 10 5 986 978 32 975 960 988 979 991 968 28 979 13 979 31 10 989 983 975 9 0 978 7 962 4 995 987 14 26 18 982 5 32 17 995 970 966 978 967 998 1 22 39 21 962 1 23 17 5 982 986 30 37 3 961 999 22 39 974 974 963 5 26 980 979 6 980 36 7 11 974 6 983 983 970 1 965 4 983 994 9 34 965 8 989 999 13 20 978 7 987 976 963 32 970 966 969 19 nil 38 9 971 968 977 8 994 17 966 985 13 960 37 982 33 991 977 963 2 16 20 992 972 973 980 20 26 962 998 979 17 9 32 1 996 997 991 979 969 981 12 14 967 967 991 978)
(def opseq [[:add 37] [:del 40] [:add -3] [:add 9] [:add 23] [:add -5] [:del 9] [:add -37] [:add -22] [:del 19] [:add -9] [:del 11] [:add 23] [:del 4] [:add 1] [:add 26] [:add 16] [:add 34] [:add -32] [:add -12] [:add -30] [:add 27] [:add -35] [:del -11] [:add -6] [:add 28] [:add -32] [:add -2] [:del 15] [:del -22] [:add 8] [:add 0] [:add 30] [:add 36] [:add -7] [:add -2] [:add -34] [:add 5] [:add 6] [:del 38] [:add 34] [:add 24] [:add 20] [:add -23] [:del -34] [:add 0] [:add 27] [:add -10] [:del 4] [:add 38] [:del -40] [:del 40] [:add 9] [:add -35] [:del -7] [:add -32] [:add -38] [:del -5] [:add -22] [:add 18] [:add -2] [:add -9] [:add -26] [:add 16] [:add -3] [:add -6] [:del 18] [:add -6] [:add 40] [:del -36] [:del -35] [:del -17] [:add 32] [:del -30] [:add -37] [:add -36] [:add 10] [:del -15] [:del -6] [:del -22] [:add -21] [:del 32] [:add -9] [:flush] [:add 26] [:add -26] [:add -17] [:add 10] [:add 24] [:add -20] [:add 3] [:add -31] [:add -11] [:add -3] [:add 28] [:add -11] [:add -2] [:add -21] [:add -37] [:add 19] [:del -30] [:add 1] [:add 27] [:del 39] [:add -13] [:add -29] [:add -6] [:add -2] [:del -17] [:add -33] [:add -7] [:add -27] [:add -22] [:add 24] [:add -38] [:add -7] [:add 6] [:del -4] [:add 1] [:add -35] [:add 30] [:add -6] [:add -16] [:add 3] [:add -15] [:del -40] [:add 22] [:add -22] [:add -35] [:add 16] [:add 18] [:add 26] [:add 24] [:add 33] [:add -9] [:del -17] [:add -13] [:add 30] [:add 32] [:add 17] [:del 38] [:add -16] [:add 6] [:add 16] [:add 36] [:add 28] [:add -19] [:add 9] [:add 30] [:del 5] [:add 4] [:add -8] [:del 24] [:add 3] [:add 38] [:add 33] [:add -35] [:add -30] [:add 9] [:add -18] [:add 26] [:add -24] [:add -30] [:add -38] [:add -32] [:add -1] [:del -22] [:add 17] [:add 9] [:add -35] [:add 0] [:add -28] [:add -5] [:add -12] [:add -18] [:add 0] [:add -7] [:add -38] [:add 29] [:add 34] [:add 38] [:add 3] [:add 30] [:add -10] [:add -26] [:del 14] [:add 4] [:add -37] [:add -12] [:add 31] [:add 12] [:del 38] [:add -36] [:add -28] [:del -31] [:flush] [:add -11] [:del 28] [:add -18] [:add 6] [:add 0] [:del 16] [:add 21] [:add 22] [:add 39] [:add -9] [:add -13] [:add -26] [:add -35] [:del 16] [:add 31] [:add 18] [:add 0] [:add -2] [:add -34] [:add 23] [:del -16] [:add -17] [:add 3] [:del -1] [:del 13] [:del -6] [:add 1] [:del 39] [:add 37] [:add -9] [:add 19] [:del 7] [:add -38] [:add 21] [:del 2] [:add 12] [:add 9] [:add 15] [:add 27] [:add 39] [:del 26] [:add -24] [:add 19] [:add -38] [:add 8] [:del -28] [:add 37] [:add -9] [:add 2] [:add -12] [:add -2] [:del -18] [:add 3] [:add -31] [:del -18] [:add -38] [:add -23] [:add 32] [:add -20] [:add 12] [:add -17] [:add 33] [:add 29] [:add -16] [:add 37] [:add -6] [:add -24] [:add 22] [:add 15] [:add -24] [:add -20] [:add 14] [:add 6] [:add -2] [:add -39] [:add 15] [:add -38] [:add 24] [:del -10] [:add -35] [:add 24] [:add 26] [:del 5] [:add -3] [:add -3] [:add -13] [:add -10] [:add 23] [:del 33] [:del 36] [:add 11] [:add 7] [:add 28] [:del -37] [:del 22] [:add 33] [:add 19] [:add 12] [:add -25] [:add -21] [:flush] [:del -31] [:del -35] [:add -27] [:add 26] [:del -7] [:add -2] [:add -35] [:add 16] [:add 10] [:add -8] [:add 16] [:del 11] [:add 25] [:add -14] [:add 8] [:add -1] [:add 22] [:add -4] [:add 9] [:add 12] [:add -13] [:add 11] [:add -35] [:add -39] [:add 40] [:add -6] [:add 25] [:add 39] [:del 20] [:add -38] [:add 26] [:add 36] [:add -31] [:add 36] [:add 37] [:add 7] [:add -13] [:add -27] [:add -16] [:add -13] [:add 18] [:add -30] [:flush] [:add 34] [:add -20] [:add 40] [:add 3] [:add -8] [:add 12] [:add -14] [:add -5] [:del -21] [:add 40] [:add -5] [:add -12] [:add -9] [:add 0] [:add -7] [:del 11] [:add -35] [:add -36] [:add -30] [:add -37] [:add -15] [:add -28] [:del -16] [:add 36] [:del 22] [:add -8] [:del -30] [:add 24] [:del -6] [:add -1] [:add -22] [:del -29] [:add 16] [:add 13] [:add 36] [:del 40] [:add 15] [:add -25] [:add 19] [:add 9] [:del -22] [:add -6] [:del -8] [:del 17] [:add 4] [:add 15] [:add -10] [:add 26] [:add -36] [:add -15] [:add -20] [:del 35] [:del -5] [:add 17] [:add 2] [:add -36] [:add -28] [:add 8] [:add -38] [:add -17] [:add -24] [:add -13] [:add -20] [:add 29] [:add -9] [:add 15] [:add -36] [:del 14] [:add -15] [:add 9] [:add -14] [:add 12] [:add 8] [:add -36] [:add -27] [:add -36] [:del -40] [:del 24] [:del 6] [:add -11] [:add -2] [:add -21] [:add -32] [:add 9] [:add -5] [:add -23] [:add -33] [:add -19] [:add -30] [:add -10] [:add -21] [:add -22] [:add -10] [:del 4] [:add 6] [:add -11] [:add 3] [:del 26] [:add -18] [:add 5] [:add -11] [:add -21] [:add 0] [:add -12] [:add -40] [:add -8] [:add 11] [:add -7] [:add 21] [:add -32] [:add -5] [:add -38] [:add -14] [:add 30] [:add -3] [:del -6] [:flush] [:add -27] [:add 4] [:add 0] [:del -1] [:add 21] [:add -8] [:del -36] [:add 5] [:add 23] [:del 20] [:add -23] [:add 8] [:add 24] [:add -3] [:add -35] [:add -31] [:add -7] [:add 30] [:add 39] [:del -18] [:add 21] [:add 26] [:add -27] [:del 14] [:add 12] [:add 18] [:add 6] [:add -4] [:add -6] [:add -19] [:del -16] [:add -29] [:add -5] [:del 16] [:add 37] [:add 39] [:add -21] [:add 13] [:add 33] [:add -33] [:add 32] [:add -2] [:add -5] [:del 23] [:add -14] [:add -14] [:add -39] [:add 12] [:add -12] [:add 33] [:add 1] [:add 29] [:add 19] [:add 3] [:add 17] [:del 37] [:add -14] [:add 5] [:del -2] [:add -3] [:add 25] [:add -26] [:add 3] [:add 30] [:add 28] [:add -16] [:del 9] [:add 40] [:add 21] [:add 15] [:add 22] [:del -19] [:add -13] [:add 8] [:add 23] [:add -26] [:add 9] [:add 2] [:add -5] [:add 3] [:add 37] [:add 23] [:add -40] [:add 8] [:add -19] [:add 23] [:del -27] [:add 9] [:add 35] [:add -29] [:add -19] [:add -11] [:add -16] [:del 27] [:add -18] [:add 26] [:add 40] [:add 34] [:del 15] [:add 20] [:add 10] [:add 40] [:del -13] [:del 4] [:add -34] [:del -19] [:add -21] [:add 1] [:add 39] [:del -24] [:del 2] [:add -5] [:add -26] [:del 35] [:add 24] [:add -4] [:add -7] [:add -26] [:add 9] [:del 0] [:add 37] [:add 1] [:add -28] [:add 18] [:add 31] [:add 31] [:add 38] [:del 28] [:add -27] [:add 38] [:add -16] [:add 5] [:add 27] [:add 27] [:add -17] [:add -1] [:add 4] [:add -32] [:del -32] [:del -18] [:add 1] [:add -3] [:add -19] [:add -10] [:add 37] [:add -16] [:add 24] [:add -30] [:del 21] [:add 35] [:add -24] [:add 11] [:add -36] [:add -24] [:add -35] [:add -36] [:add 8] [:add 40] [:add -22] [:add 10] [:add 6] [:del -22] [:del -12] [:add 5] [:add 21] [:add 27] [:add 11] [:add -17] [:del 37] [:add -7] [:add 24] [:add 13] [:add -26] [:add 32] [:del 27] [:add -28] [:add -8] [:add 14] [:add -31] [:add -2] [:add -24] [:del 2] [:add -38] [:del 23] [:del -30] [:add 28] [:add -34] [:add 18] [:del -27] [:del 23] [:add -19] [:del 20] [:add -31] [:add 0] [:add 1] [:del -26] [:add 39] [:add 26] [:del 31] [:add 35] [:del -34] [:del 1] [:del 24] [:del 23] [:add -14] [:add -2] [:del -37] [:del 17] [:add -7] [:add 7] [:add 35] [:add 8] [:add -19] [:add -11] [:add -12] [:add -22] [:add 32] [:del 20] [:add 7] [:add -36] [:add -30] [:add 10] [:add -23] [:add 4] [:add 19] [:add -17] [:add 25] [:add 18] [:add -1] [:add -37] [:add -25] [:add -37] [:add -34] [:add 21] [:add -27] [:add -27] [:add -18] [:add -31] [:add 38] [:add -17] [:add -2] [:add -15] [:add 36] [:del -23] [:add 34] [:add -1] [:add -36] [:del 15] [:add 29] [:add 17] [:add -22] [:add 37] [:add 15] [:del -23] [:add 30] [:add -4] [:add 3] [:add 4] [:add -2] [:add 20] [:add -16] [:del 39] [:add -1] [:add 3] [:del -14] [:add -38] [:del -25] [:add 29] [:add -24] [:del 26] [:add 35] [:del 7] [:add -5] [:del 7] [:add 40] [:add -33] [:add 34] [:add 26] [:add 34] [:del 20] [:add 31] [:add 33] [:add 38] [:add 19] [:del 17] [:add 19] [:add -25] [:add -10] [:add -37] [:add 8] [:add -21] [:add 13] [:add 25] [:add 30] [:add 25] [:add -26] [:del -25] [:del -34] [:add 5] [:add -35] [:add 39] [:add -33] [:add 27] [:del 40] [:flush] [:add -34] [:add 10] [:del -21] [:add -8] [:add 22] [:add -4] [:add 38] [:add -17] [:add -34] [:add 34] [:add -20] [:add 30] [:del -1] [:add -3] [:del 7] [:add 21] [:del 24] [:add 17] [:del -38] [:add -2] [:del 9] [:del 11] [:add -2] [:add 3] [:add 35] [:add 8] [:del -6] [:add -37] [:add 17] [:del -35] [:add -32] [:add -13] [:add 7] [:add 21] [:del -18] [:del 34] [:add -26] [:del -27] [:add -21] [:add 4] [:add -36] [:del -4] [:add 9] [:del -21] [:add -15] [:add -4] [:del 4] [:add 0] [:add 15] [:add -30] [:del 12] [:del 33] [:add -22] [:add -19] [:add -22] [:add -12] [:add -2] [:flush] [:add -20] [:add -20] [:add -33] [:add -17] [:del 1] [:add -2] [:add 38] [:add -39] [:add 14] [:add -8] [:add -19] [:add 7] [:add -13] [:del 39] [:add 38] [:del -14] [:del -40] [:del -18] [:add 38] [:del 0] [:add 37] [:add -16] [:add -29] [:del 30] [:add 16] [:add -31] [:del -2] [:add 37] [:add -10] [:add -26] [:add 33] [:add -23] [:add -14] [:add -18] [:del 10] [:add 5] [:add -14] [:add -22] [:del 32] [:add -25] [:add -40] [:add -12] [:add -21] [:add -9] [:add -32] [:add 28] [:add -21] [:add 13] [:del -21] [:del 31] [:add 10] [:add -11] [:del -17] [:add -25] [:add 9] [:add 0] [:add -22] [:del 7] [:add -38] [:add 4] [:add -5] [:add -13] [:add 14] [:del 26] [:add 18] [:add -18] [:add 5] [:add 32] [:add 17] [:add -5] [:del -30] [:add -34] [:add -22] [:add -33] [:add -2] [:del 1] [:add 22] [:add 39] [:add 21] [:add -38] [:add 1] [:add 23] [:add 17] [:add 5] [:del -18] [:add -14] [:add 30] [:del 37] [:add 3] [:add -39] [:add -1] [:del 22] [:add 39] [:del -26] [:del -26] [:add -37] [:del 5] [:add 26] [:add -20] [:add -21] [:del 6] [:add -20] [:add 36] [:add 7] [:del 11] [:add -26] [:del 6] [:add -17] [:del -17] [:add -30] [:add 1] [:del -35] [:add 4] [:del -17] [:add -6] [:del 9] [:add 34] [:add -35] [:add 8] [:add -11] [:del -1] [:add 13] [:add 20] [:add -22] [:add 7] [:add -13] [:add -24] [:add -37] [:add 32] [:add -30] [:del -34] [:del -31] [:add 19] [:flush] [:add 38] [:add 9] [:add -29] [:add -32] [:del -23] [:del 8] [:del -6] [:add 17] [:add -34] [:add -15] [:del 13] [:add -40] [:add 37] [:add -18] [:add 33] [:add -9] [:add -23] [:add -37] [:add 2] [:add 16] [:add 20] [:add -8] [:add -28] [:add -27] [:add -20] [:add 20] [:add 26] [:add -38] [:add -2] [:add -21] [:add 17] [:add 9] [:del 32] [:del 1] [:add -4] [:add -3] [:del -9] [:add -21] [:add -31] [:add -19] [:add 12] [:add 14] [:add -33] [:del -33] [:del -9] [:add -22]])

  )
