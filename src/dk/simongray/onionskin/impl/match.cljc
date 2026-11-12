(ns ^:no-doc dk.simongray.onionskin.impl.match
  "Pairing the elements of two captures of a page whose markup may differ.

  One new wrapper element changes the path of every element inside it, so
  paths only pair the elements of two captures while the markup stays the
  same. The rules here pair elements by what they are and what they hold,
  from the most trusted rule to the least. They leave an element unpaired
  when the evidence is ambiguous, since a wrong pair hides a change while a
  missing pair shows up in the diff.

  A tree describes the nodes of a capture by their index in document order.
  A state holds the two trees as :a and :b, the pairs so far in both
  directions as :ab and :ba, and the :rule that made each pair."
  (:require [clojure.string :as str]))

(defn- tree
  "The tree of `nodes`, the elements of a capture in document order.

  Each key holds a vector with one value per node: the index of its :parent,
  the indexes of its :children, its :tag and the :size of its subtree. Two
  nodes have the same value under :exact when their subtrees have the same
  tags, attributes and text, and the same value under :shape when the tags
  alone are the same."
  [nodes]
  (let [nodes    (vec nodes)
        n        (count nodes)
        index    (into {} (map-indexed (fn [i node] [(:path node) i])) nodes)
        parent   (mapv (fn [{:keys [path]}]
                         (some->> (str/last-index-of path ">") (subs path 0) index))
                       nodes)
        children (reduce (fn [v i]
                           (if-let [p (parent i)]
                             (update v p conj i)
                             v))
                         (vec (repeat n []))
                         (range n))
        tag      (mapv (fn [{:keys [path]}]
                         (let [step (subs path (inc (or (str/last-index-of path ">") -1)))]
                           (subs step 0 (str/last-index-of step ":"))))
                       nodes)
        ;; Children come after their parents, so the last node goes first.
        upwards  (fn [f]
                   (reduce (fn [v i] (assoc v i (f i (mapv v (children i)))))
                           (vec (repeat n nil))
                           (range (dec n) -1 -1)))]
    {:nodes    nodes
     :parent   parent
     :children children
     :tag      tag
     :size     (upwards (fn [_ sizes] (apply + 1 sizes)))
     :exact    (upwards (fn [i below]
                          [(tag i) (:attributes (nodes i)) (:text (nodes i)) below]))
     :shape    (upwards (fn [i below] [(tag i) below]))}))

(defn- pair
  "Pair node `i` of the first tree with node `j` of the second in `state` by
  `rule`, unless one of them has a partner already."
  [{:keys [ab ba] :as state} rule i j]
  (if (or (contains? ab i) (contains? ba j))
    state
    (-> state
        (assoc-in [:ab i] j)
        (assoc-in [:ba j] i)
        (assoc-in [:rule i] rule))))

(defn- pair-subtrees
  "Pair the nodes of the subtrees at `i` and `j` in `state` by `rule`, one by
  one in document order. The two subtrees must have the same shape."
  [state rule i j]
  (reduce #(pair %1 rule (+ i %2) (+ j %2))
          state
          (range (get-in state [:a :size i]))))

(defn- singles
  "The unpaired nodes of `tree` that alone have their value of `f`, by that
  value. The nodes in `paired` have a partner."
  [f tree paired]
  (into {}
        (keep (fn [[v [i & more]]]
                (when (and (some? v) (nil? more))
                  [v i])))
        (group-by #(f tree %)
                  (remove #(contains? paired %) (range (count (:nodes tree)))))))

(defn- pair-singles
  "Pair the nodes of `state` that alone in their capture have their value of
  `f`, when both captures have the value. The largest subtrees pair first,
  by `rule` and with `join`, which is pair or pair-subtrees."
  [{:keys [a b ab ba] :as state} rule join f]
  (let [theirs (singles f b ba)]
    (reduce (fn [state [v i]]
              (if-let [j (theirs v)]
                (join state rule i j)
                state))
            state
            (sort-by (fn [[_ i]] [(- (get-in a [:size i])) i])
                     (singles f a ab)))))

(defn- pair-siblings
  "Pair the unpaired children of each pair in `state` that have the same
  value of `f`, in order, when `enough?` accepts how many there are on each
  side. They pair by `rule` and with `join`, which is pair or
  pair-subtrees."
  [{:keys [a b] :as state} rule join f enough?]
  (reduce-kv
    (fn [{:keys [ab ba] :as state} i j]
      (let [group  (fn [tree paired parent]
                     (group-by #(f tree %)
                               (remove #(contains? paired %)
                                       (get-in tree [:children parent]))))
            theirs (group b ba j)]
        (reduce (fn [state [v is]]
                  (let [js (theirs v)]
                    (if (and js (enough? (count is) (count js)))
                      (reduce (fn [state [i j]] (join state rule i j))
                              state
                              (map vector is js))
                      state)))
                state
                (group a ab i))))
    state
    (:ab state)))

(defn- flip
  "The `state` seen from the second tree."
  [{:keys [a b ab ba] :as state}]
  (assoc state :a b :b a :ab ba :ba ab))

(defn- container
  "The node of the second tree in `state` that holds what node `i` of the
  first holds, or nil when there's no clear one.

  It's the unpaired node with the same tag whose descendants are most
  paired with those of `i`. More than half of both must be paired with each
  other, and no other node may do equally well."
  [{:keys [a b ab ba]} i]
  (let [below   (fn [tree k] (range (inc k) (+ k (get-in tree [:size k]))))
        above   (fn [k] (take-while some? (iterate #(get-in b [:parent %]) k)))
        holds   (frequencies
                  (for [partner (keep ab (below a i))
                        j       (above partner)
                        :when (and (not (contains? ba j))
                                   (= (get-in a [:tag i]) (get-in b [:tag j])))]
                    j))
        ;; Falleri et al. (ASE 2014) call this share the dice value.
        share   (fn [[j shared]]
                  (/ (* 2.0 shared)
                     (+ (count (below a i)) (count (below b j)))))
        [[j best] [_ next-best]] (sort-by (comp - second)
                                          (map (juxt first share) holds))]
    (when (and j (> best 0.5) (not= best next-best))
      j)))

(defn- pair-containers
  "Pair the nodes of `state` that hold the same paired nodes, from the last
  node up, so that a container counts towards the ones around it. Each node
  must be the other's choice."
  [state]
  (reduce (fn [state i]
            (let [j (when-not (contains? (:ab state) i)
                      (container state i))]
              (if (and j (= i (container (flip state) j)))
                (pair state :container i j)
                state)))
          state
          (range (dec (count (get-in state [:a :nodes]))) -1 -1)))

(defn- held
  "A function of a tree and a node's index that returns what the tree holds
  for the node under `k`."
  [k]
  (fn [tree i] (get-in tree [k i])))

;; The order follows GumTree (Falleri et al., ASE 2014; Falleri and
;; Martinez, ICSE 2024): identical subtrees first, then containers through
;; their paired descendants, then the children that are left. Text and
;; boxes come last and only for elements that nothing else paired, since a
;; diff reports changes to boxes.
(defn- rules
  "The functions of a state that pair more of its nodes, from the most
  trusted to the least, with `ks` as the attributes that name an element."
  [ks]
  (concat
    (for [k ks]
      #(pair-singles % :key pair (fn [tree i] (get-in tree [:nodes i :attributes k]))))
    [#(pair-singles % :subtree pair-subtrees (held :exact))
     #(pair-siblings % :subtree pair-subtrees (held :exact) (constantly true))
     pair-containers
     #(pair-siblings % :shape pair-subtrees (held :shape) =)
     #(pair-siblings % :tag pair (held :tag) (fn [n m] (= 1 n m)))
     #(pair-singles % :text pair (fn [tree i]
                                   (when-let [text (get-in tree [:nodes i :text])]
                                     [(get-in tree [:tag i]) text])))
     #(pair-singles % :box pair (fn [tree i]
                                  [(get-in tree [:tag i]) (get-in tree [:nodes i :box])]))]))

(defn- settle
  "Apply the first of `rules` that pairs more nodes of `state`, again and
  again until none does. A weaker rule therefore only gets its turn when
  the stronger ones have nothing left to pair."
  [state rules]
  (let [more (fn [rule]
               (let [state' (rule state)]
                 (when (> (count (:ab state')) (count (:ab state)))
                   state')))]
    (if-let [state' (some more rules)]
      (recur state' rules)
      state)))

(defn- moved?
  "True if node `i` of `state` and its partner sit in places that aren't
  partners: the nearest paired node above the one isn't paired with the
  nearest paired node above the other. A new wrapper doesn't count."
  [{:keys [a b ab ba]} i]
  (let [above (fn [tree paired k]
                (->> (iterate #(get-in tree [:parent %]) k)
                     (take-while some?)
                     (rest)
                     (filter #(contains? paired %))
                     (first)))]
    (not= (ab (above a ab i)) (above b ba (ab i)))))

(defn pairs
  "The elements that `before` and `after`, the nodes of two captures, have
  in common, with `ks` as the attributes that name an element.

  Each pair is a map of the node :before, the node :after and the :rule that
  paired them, in the order of `before`. A pair that sits in another place
  of the document is also :moved?. While the markup is the same, the nodes
  pair by :path. Otherwise the roots pair, and then these rules apply in
  order:

      :key        the same value of a key attribute, which no other element
                  of either capture has
      :subtree    identical tags, attributes and text all the way down, in
                  one place of each capture or among the children of a pair
      :container  the same tag, with more than half of what's inside paired
      :shape      identical tags all the way down, among the children of a
                  pair, when as many have that shape on both sides
      :tag        the only unpaired child of a pair with its tag
      :text       the only unpaired element with its tag and text
      :box        the only unpaired element with its tag and box"
  [before after ks]
  (cond
    (= (map :path before) (map :path after))
    (mapv (fn [a b] {:before a :after b :rule :path}) before after)

    (or (empty? before) (empty? after))
    []

    :else
    (let [state (-> {:a (tree before) :b (tree after) :ab {} :ba {} :rule {}}
                    (pair :root 0 0)
                    (settle (rules ks)))]
      (mapv (fn [[i j]]
              (cond-> {:before (get-in state [:a :nodes i])
                       :after  (get-in state [:b :nodes j])
                       :rule   (get-in state [:rule i])}
                (moved? state i) (assoc :moved? true)))
            (sort (:ab state))))))
