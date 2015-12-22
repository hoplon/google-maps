(set-env!
  :resource-paths #{"src"}
  :dependencies '[[adzerk/bootlaces     "0.1.12"         :scope "test"]
                  [hoplon/boot-hoplon   "0.1.9"         :scope "test"]
                  [hoplon               "6.0.0-alpha10"]
                  [cljsjs/google-maps   "3.18-0"]
                  [hoplon/google-loader "0.2.0"]])

(require '[adzerk.bootlaces :refer :all]
         '[hoplon.boot-hoplon :refer :all])

(def +version+ "3.18.0-2")

(task-options!
 pom  {:project     'hoplon/google-maps
       :version     +version+
       :description "hoplon google maps component"
       :url         "https://developers.google.com/maps/documentation/javascript/"
       :scm         {:url "https://github.com/hoplon/google-maps"}
       :license     {"" ""}})

