(ns proton.lib.atom
  (:require [proton.lib.helpers :refer [generate-div process->html deep-merge console!]]
            [cljs.nodejs :as node]
            [clojure.string :as string :refer [lower-case upper-case]]))

(def commands (.-commands js/atom))
(def workspace (.-workspace js/atom))
(def keymaps (.-keymaps js/atom))
(def views (.-views js/atom))
(def config (.-config js/atom))
(def grammars (.-grammars (.-grammars js/atom)))
(def workspace-view (.getView views workspace))
(def packages (.-packages js/atom))

(def element (atom (generate-div "test" "proton-which-key")))
(def bottom-panel (atom (.addBottomPanel workspace
                                       (clj->js {:visible false
                                                  :item @element}))))

(def modal-element (atom (generate-div "test" "proton-modal-panel")))
(def modal-panel (atom (.addModalPanel workspace (clj->js {:visible false
                                                           :item @modal-element}))))

(defn update-bottom-panel [html] (aset @element "innerHTML" html))
(defn update-modal-panel [html] (aset @modal-element "innerHTML" html))
(defn show-modal-panel [] (.show @modal-panel))
(defn hide-modal-panel [] (.hide @modal-panel))
(defn show-bottom-panel [] (.show @bottom-panel))
(defn hide-bottom-panel [] (.hide @bottom-panel))

(defn get-apm-path []
  (.getApmPath packages))

(def steps (atom []))

(defn reset-process-steps! [] (reset! steps []))
(defn insert-process-step!
  ([text] (insert-process-step! text "[..]"))

  ([text status]
   (swap! steps conj [text status])
   (update-modal-panel (process->html @steps))))

(defn amend-last-step!
  ([text] (amend-last-step! text "[..]"))

  ([text status]
   (swap! steps #(conj (into [] (butlast %)) [text status]))
   (update-modal-panel (process->html @steps))))

(defn mark-last-step-as-completed! []
  (amend-last-step! (str (get (last @steps) 0)) "[ok]"))

(defn activate-proton-mode! []
  (console! "Chain activated!")
  (let [classList (.-classList workspace-view)]
    (.add classList "proton-mode")
    (show-bottom-panel)))

(defn deactivate-proton-mode! []
  (console! "Chain deactivated!")
  (let [classList (.-classList workspace-view)]
    (.remove classList "proton-mode")
    (hide-bottom-panel)))

(defn is-proton-mode-active? []
  (let [classList (array-seq (.-classList workspace-view))]
    (not (nil? (some #{"proton-mode"} classList)))))

(defn eval-action! [{:keys [action target fx]}]
  (let [selector (when (string? target) (js/document.querySelector target))
        dom-target (if (nil? target) (.getView views workspace) (or selector (target js/atom)))]

    ;; functions always go first
    (if (not (nil? fx))
      (fx)
      (do
        (console! (str "Dispatching: " action))
        (.dispatch commands dom-target action)))
    (deactivate-proton-mode!)))

(defn get-all-settings []
  (let [config-obj (.getAll config)
        parsed-config (atom [])]
    ;; First level are different selectors. The most interesting one being '*'
    (goog.object/forEach config-obj
      (fn [subobj _ _]
        ;; second level are the actual config objects inside the selectorso
        ;; .value holds the actual configuration object for the selector
        (goog.object/forEach (.-value subobj)
          (fn [obj config-prefix _]
            ;; third level is the actual config string. Like core.{xxx}
            (goog.object/forEach obj
              (fn [val config-postfix _]
                (swap! parsed-config conj (str config-prefix "." config-postfix))))))))
    @parsed-config))

(defn get-config [selector]
  (.get config selector))

(defn set-config! [selector value]
  (console! (str "Setting " selector " to " (clj->js value)))
  (.set config selector (clj->js value)))

(defn add-to-config! [selector value]
  (let [previous-config (js->clj (.get config selector))]
    (set-config! selector (conj previous-config value))))

(defn remove-val-from-config! [selector value]
  (let [config (js->clj (.get config selector))
        new-config (filter #(not (= value %)) config)]
      (set-config! selector new-config)))

(defn unset-config! [selector]
  (.unset config selector))

(defn set-keymap!
  ([selector bindings]
   (set-keymap! selector bindings 0))
  ([selector bindings priority]
   (let [binding-map (reduce deep-merge (map #(hash-map (get % 0) (get % 1)) bindings))
         selector-bound-map (hash-map selector binding-map)]
    (.add keymaps "custom-keymap" (clj->js selector-bound-map) priority))))

(defn clear-keymap! []
  (.removeBindingsFromSource keymaps "custom-keymap"))

(defn get-active-editor []
  (if-let [editor (.getActiveTextEditor workspace)]
   (if-not (.isMini editor) editor nil)
   nil))

(defn find-grammar-by-name [name]
 (first (filter #(= (.-name %) name) grammars)))

(defn set-grammar [grammar]
  (if-let [editor (get-active-editor)]
    (if (string? grammar)
     (.setGrammar editor (find-grammar-by-name grammar))
     (.setGrammar editor grammar))))

(defn is-activated? [package-name]
  (.isPackageActive packages package-name))

(defn is-package-disabled? [package-name]
  (.isPackageDisabled packages package-name))

(defn get-all-packages []
  (.getAvailablePackageNames packages))

(defn get-package [package-name]
  (.getLoadedPackage packages package-name))

(defn is-installed? [package-name]
  (if (.isPackageLoaded packages package-name)
    true
    (let [pkgs (get-all-packages)]
      (not (= -1 (.indexOf pkgs package-name))))))

(defn enable-package [package-name]
  (console! (str "enabling package " (name package-name)))
  (.enablePackage packages package-name))


(defn disable-package
  ([package-name]
   (disable-package package-name false))

  ([package-name force]
   (if (or (is-activated? package-name) force)
       (do
         (when-not (is-package-disabled? package-name)
          (do
            (console! (str "disabling package " package-name))
            (.disablePackage packages package-name)))))))

(defn is-activated? [package-name]
  (.isPackageActive packages package-name))

(defn reload-package [package-name]
  (disable-package package-name)
  (enable-package package-name))

(defn force-reload-package [package-name]
  (disable-package package-name true)
  (enable-package package-name))
