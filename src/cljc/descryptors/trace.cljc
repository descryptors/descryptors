(ns descryptors.trace
  ;;#?(:cljs (:require [day8.re-frame.tracing :as tracing :refer-macros [fn-traced]]))
  )


#_(defmacro traced [& args]
  #?(:cljs `(fn-traced ~@args)
     :clj  `(fn ~@args)))



(defmacro traced [& args]
  `(fn ~@args))
