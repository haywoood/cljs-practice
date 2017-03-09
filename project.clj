(defproject medal_count "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.9.0-alpha14"]
                 [org.clojure/clojurescript "1.9.495"]
                 [reagent "0.6.0"]
                 [re-frame "0.9.2"]
                 [cljs-ajax "0.5.8"]]

  :plugins [[lein-cljsbuild "1.1.5"]]

  :source-paths ["src/cljs"]

  :clean-targets ^{:protect false} [:target-path :compile-path "resources/public/js"]

  ;; nREPL by default starts in the :main namespace, we want to start in `user`
  ;; because that's where our development helper functions like (run) and
  ;; (browser-repl) live.
  :repl-options {:init-ns user}

  :cljsbuild {:builds
              [{:id "app"
                :source-paths ["src/cljs"]

                :figwheel true

                :compiler {:main medal-count.core
                           :asset-path "js/compiled/out"
                           :output-to "resources/public/js/compiled/medal_count.js"
                           :output-dir "resources/public/js/compiled/out"
                           :source-map-timestamp true}}

               {:id "min"
                :source-paths ["src/cljs"]
                :jar true
                :compiler {:main medal-count.core
                           :output-to "resources/public/js/compiled/medal_count.js"
                           :output-dir "target"
                           :source-map-timestamp true
                           :optimizations :advanced
                           :pretty-print false}}]}

  :figwheel {:css-dirs ["resources/public/css"]}  ;; watch and update CSS

  :profiles {:dev
             {:dependencies [[figwheel "0.5.9"]
                             [figwheel-sidecar "0.5.9"]
                             [com.cemerick/piggieback "0.2.1"]
                             [org.clojure/tools.nrepl "0.2.12"]]

              :plugins [[lein-figwheel "0.5.9"]]

              :source-paths ["dev"]
              :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}}})
