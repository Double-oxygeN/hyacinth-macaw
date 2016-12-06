(defproject hyacinth-macaw "0.2.0"
  :description "HYACINTH MACAW: Twitter Client for Budgerigar Bulletin"
  :url "https://github.com/Double-oxygeN/hyacinth-macaw"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/core.async "0.2.395"]
                 [org.clojure/data.json "0.2.6"]
                 [org.twitter4j/twitter4j-core "4.0.4"]
                 [org.twitter4j/twitter4j-stream "4.0.4"]
                 [com.kohlschutter.junixsocket/junixsocket-native-common "2.0.4"]]
  :main ^:skip-aot hyacinth-macaw.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
