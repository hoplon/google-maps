(ns hoplon.google.jsapi.maps
  (:refer-clojure :exclude [clj->js])
  (:require
    [clojure.string :as string]
    [javelin.core   :as j       :refer [defc defc= cell= cell cell-doseq with-let]]
    [hoplon.core    :as h       :refer [div defelem]]
    [hoplon.google.jsapi.loader :refer [queued ensure-api api-key]]))

(defn clj->js
  "Recursively transforms ClojureScript maps into Javascript objects,
   other ClojureScript colls into JavaScript arrays, and ClojureScript
   keywords into JavaScript strings."
  [x]
  (cond
    (string? x)  x
    (keyword? x) (name x)
    (map? x) (with-let [obj (js-obj)]
               (doseq [[k v] x]
                 (aset obj (clj->js k) (clj->js v))))
    (or (seq? x) (vector? x)) (apply array (map clj->js x))
    :else x))

(defc  maps-version "3")
(defc=  maps-options {:other_params (str "libraries=geometry" (when api-key (str "&key=" api-key)))})

(def ensure-maps
  (queued
    (fn [callback]
      (ensure-api
        #(.load js/google "maps" @maps-version
           (clj->js (assoc @maps-options :callback callback)))))))

(defn- dom2str [elem] (.-innerHTML (div elem)))
(defn- indexed [coll] (map-indexed vector coll))

(defn delay-until [ready? f & args]
  (let [doit    (memoize #(or (apply f args) ::ok))]
    #(cell= (when ready? (doit)))))

(defn starts-with? [string prefix]
  (= 0 (.lastIndexOf string prefix 0)))

(defn visible? [elem & [msg]]
  (with-let [c (cell nil)]
    (h/with-interval 100
      (reset! c (.is (js/jQuery elem) ":visible"))
      (when (and msg @c) (.log js/console msg)))))

(defn decode-path [coded]
  (let [maps (.. js/google -maps)
        LatLng (.-LatLng maps)
        enc-lib (.-encoding (.-geometry maps))
        points (.decodePath enc-lib coded)]
    (into
      []
      (map
        #(assoc {} :lat (.lat (clj->js %)) :lng (.lng (clj->js %)))
        points))))

(defn point-in-polygon [point path]
  (let [maps              (.. js/google -maps)
        LatLng            (.-LatLng maps)
        Polygon           (.-Polygon maps)
        poly-lib          (.-poly (.-geometry maps))
        lat-lng           #(LatLng. (js/parseFloat %1) (js/parseFloat %2))
        gon               (Polygon. (clj->js {:path (mapv #(lat-lng (:lat %) (:lng %)) path)}))
        pnt               (lat-lng (:lat point) (:lng point))
        res               (.containsLocation poly-lib pnt gon)]
    res))

(defmulti layer (fn [x y imap] x))

(defmethod layer :hoplon.google.jsapi.maps/layer-bicycling [x y imap]
  (let [maps          (.. js/google -maps)
        Bicycling     (.-BicyclingLayer maps)
        bicycling     (Bicycling.)]
    (cell= (let [map      (when y imap)]
             (.setMap bicycling map)))))

(defmethod layer :hoplon.google.jsapi.maps/layer-traffic [x y imap]
  (let [maps          (. js/google -maps)
        Traffic       (.-TrafficLayer maps)
        traffic       (Traffic.)]
    (cell= (let [map      (when y imap)]
             (.setMap traffic map)))))

(defmethod layer :hoplon.google.jsapi.maps/layer-transit [x y imap]
  (let [maps          (. js/google -maps)
        Transit       (.-TransitLayer maps)
        transit       (Transit.)]
    (cell= (let [map      (when y imap)]
             (.setMap transit map)))))

(defelem google-map-control
  [attr kids]
  (assoc {} :position (keyword (:position attr)) :controls kids))

(defelem google-map [attr kids]
  (with-let [elem       (div (select-keys attr (keys (filter #(not= "hoplon.google.jsapi.maps" (namespace (key %))) attr))))]
    (let [visible?      (visible? elem)
          center        (:hoplon.google.jsapi.maps/center attr)
          map-opts      (:hoplon.google.jsapi.maps/opts attr)
          fit-pins      (:hoplon.google.jsapi.maps/fit-pins attr)
          markers       (:hoplon.google.jsapi.maps/markers attr)
          polylines     (:hoplon.google.jsapi.maps/polylines attr)
          circles       (:hoplon.google.jsapi.maps/circles attr)
          polygons      (:hoplon.google.jsapi.maps/polygons attr)
          controls      (:hoplon.google.jsapi.maps/controls attr)
          mkfilter      #(fn [[x _]]
                           (let [ns (namespace x) nm (name x)]
                             (and (= "hoplon.google.jsapi.maps" ns)
                               (starts-with? nm %))))
          layers        (filter (mkfilter "layer-") attr)
          map-callbacks (filter (mkfilter "map-") attr)
          cir-callbacks (filter (mkfilter "cir-") attr)
          pln-callbacks (filter (mkfilter "polyline-") attr)
          pgn-callbacks (filter (mkfilter "polygon-") attr)
          pin-callbacks (filter (mkfilter "marker-") attr)
          rm-pfx        #(string/replace % #"^[^-]+-" "")]
      (ensure-maps
        (delay-until visible?
          (fn []
            (let [maps           (.. js/google -maps)
                  Map            (.-Map maps)
                  Event          (.-event maps)
                  LatLng         (.-LatLng maps)
                  Marker         (.-Marker maps)
                  Polyline       (.-Polyline maps)
                  Polygon        (.-Polygon maps)
                  Circle         (.-Circle maps)
                  InfoWindow     (.-InfoWindow maps)
                  iw             (InfoWindow. (clj->js {}))
                  LatLngBounds   (.-LatLngBounds maps)
                  lat-lng        #(LatLng. (js/parseFloat %1) (js/parseFloat %2))
                  opts           (cell= (let [{:keys [lat lng]} center]
                                          (clj->js (merge {} map-opts {:center (lat-lng lat lng)}))))
                  imap           (Map. elem @opts)
                  map-ctrls      (.-controls imap)
                  bounds         (cell= (with-let [b (LatLngBounds.)]
                                          (doseq [{:keys [lat lng]} markers]
                                            (.extend b (lat-lng lat lng)))))
                  cp             {:TL google.maps.ControlPosition.TOP_LEFT, :BC google.maps.ControlPosition.BOTTOM_CENTER, :LC google.maps.ControlPosition.LEFT_CENTER,
                                  :BL google.maps.ControlPosition.BOTTOM_LEFT, :LB google.maps.ControlPosition.LEFT_BOTTOM, :BR google.maps.ControlPosition.BOTTOM_RIGHT,
                                  :TR google.maps.ControlPosition.TOP_RIGHT, :LT google.maps.ControlPosition.LEFT_TOP, :TC google.maps.ControlPosition.TOP_CENTER,
                                  :RC google.maps.ControlPosition.RIGHT_CENTER, :RT google.maps.ControlPosition.RIGHT_TOP, :RB google.maps.ControlPosition.RIGHT_BOTTOM}]

              ;;;; Map Callbacks
              (doseq [x map-callbacks]
                (let [evt (rm-pfx (name (key x)))
                      fun (val x)
                      wrp (fn [& args] (apply fun imap args)) ]
                  (.addListener Event imap evt wrp)))

              ;;;;; Layers
              (mapv #(layer (key %) (val %) imap) layers)

              ;;;;;; Controls
              (doseq [x controls]
                (let [pos (:position x) ctrls (:controls x) ]
                  (.push
                    (aget map-ctrls (pos cp))
                    (div ctrls))))

              ;;;;;; Polylines
              (cell-doseq [[i {:keys [path opts] :as pline}] (cell= (indexed polylines))]
                (let [polyline  (Polyline. (clj->js {}))]
                  (doseq [x pln-callbacks]
                    (let [evt (rm-pfx (name (key x)))
                          fun (val x)
                          wrp (fn [& args] (apply fun imap polyline pline args))]
                      (.addListener Event polyline evt wrp)))
                  (cell=
                    ((~(partial delay-until visible?)
                      #(let [map  (when path imap)
                             path (if path (mapv (fn [p] (lat-lng (:lat p) (:lng p))) path) [])
                             opt  (clj->js (merge {} opts {:map map :path path}))]
                        (.setOptions polyline opt)))))))

              ;;;;;;; Circles
              (cell-doseq [[i {:keys [opts] :as circ}] (cell= (indexed circles))]
                (let [circle (Circle. (clj->js {}))
                      _      (cell= (.log js/console (clj->js circ)))]
                  (doseq [x cir-callbacks]
                    (let [evt (rm-pfx (name (key x)))
                          fun (val x)
                          wrp (fn [& args] (apply fun imap circle circ args))]
                      (.addListener Event circle evt wrp)))
                  (cell=
                    ((~(partial delay-until visible?)
                      #(let [map  imap
                             opt  (clj->js (merge {} opts {:map map}))]
                      (.setOptions circle opt)))))))


              ;;;;;;; Polygons
              (cell-doseq [[i {:keys [path opts] :as pgon}] (cell= (indexed polygons))]
                (let [ polygon (Polygon. (clj->js {}))]
                  (doseq [x pgn-callbacks]
                    (let [evt (rm-pfx (name (key x)))
                          fun (val x)
                          wrp (fn [& args] (apply fun imap polygon pgon args))]
                      (.addListener Event polygon evt wrp)))
                  (cell=
                    ((~(partial delay-until visible?)
                      #(let [map  (when path imap)
                             path (if path (mapv (fn [p] (lat-lng (:lat p) (:lng p))) path) [])
                             opt  (clj->js (merge {} opts {:map map :path path}))]
                      (.setOptions polygon opt)))))))

              ;;;;;; Options
              (cell= ((~(partial delay-until visible?) #(.setOptions imap opts))))

              ;;;;;; fit-pins
              (cell= (when (and (seq markers) fit-pins)
                       ((~(partial delay-until visible?)
                         #(.fitBounds imap bounds)))))

              ;;;;; Markers
              (cell-doseq [[i {:keys [lat lng info opts] :as pin}] (cell= (indexed markers))]
                (let [marker  (Marker. (clj->js {}))
                      info    (cell= info)]
                  (doseq [x pin-callbacks]
                    (let [evt  (rm-pfx (name (key x)))
                          fun  (val x)
                          wrp  (fn [& args] (apply fun imap marker iw @pin args))]
                      (.addListener Event marker evt wrp)))
                  (.addListener Event marker "click"
                    (fn []
                      (when @info
                        (.setContent iw @info)
                        (.open iw imap marker))))
                  (cell= ((~(partial delay-until visible?)
                          #(let [map (when lat imap)
                                pos (when lat (lat-lng lat lng))
                                opt (clj->js (merge {} opts {:map map :position pos}))]
                            (.close iw)
                            (.setOptions marker opt))))))))))))))
