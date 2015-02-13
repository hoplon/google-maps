(set-env!
  :resource-paths #{"src"}
  :dependencies '[[adzerk/bootlaces           "0.1.9"         :scope "test"]
                  [tailrecursion/boot-hoplon  "0.1.0"         :scope "test"]
                  [tailrecursion/hoplon       "6.0.0-SNAPSHOT"]
                  [cljsjs/google-maps         "3.18-0"]
                  [hoplon/hoplon-google-loader  "0.1.0"]])

(require '[adzerk.bootlaces :refer :all]
         '[tailrecursion.boot-hoplon :refer :all])

(def +version+ "3.18.0")

(task-options!
 pom  {:project     'hoplon/hoplon-google-maps
       :version     +version+
       :description "hoplon google maps component"
       :url         "https://developers.google.com/maps/documentation/javascript/"
       :scm         {:url "https://github.com/hoplon/hoplon-google-maps"}
       :license     {"" ""}})

