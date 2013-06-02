(ns hotel_nlp.core
   (:require  [hotel_nlp.protocols :refer :all]
              ;[hotel_nlp.externals.bindings :refer :all]
             ; [hotel_nlp.concretions.models]
              [hotel_nlp.helper :as help]
   )
   (:import [hotel_nlp.helper Workflow])
)


(definline component? 
"Tests if c satisfies IComponent." [c]
 `(satisfies? IComponent ~c)) 
 
(defn fn->component [f]
(reify IComponent
 (link [this pos other]
   (Workflow. (help/link* this pos other))) 
 (run [_ args] 
   (f args)))) 

  
(defmacro defcomponent
"Defines a top-level Component with the specified name. 
 co must satisfy IComponent. The only thing this does over a plain 'def' is checking whether co is an actual Component." 
([name co]
 (let [c (eval co)]  
  (assert (component? c) "Not a valid IComponent")
 `(def ~name ~c)))
([name doc-s co]
 (let [c (eval co)]  
  (assert (component? c) "Not a valid IComponent")
 `(def ~name ~doc-s ~c))))    
 

(defmacro defworkflow 
"Defines a top-level Workflow with the specified name containing the given Components. First element in components can be a doc-string for this workflow."  
[name & components]
(let [[doc & comps :as all] (eval (vec components)) 
       cs  (if (string? doc) [comps true] [all false])
       ds  (if (second cs) doc "Undocumented workflow.")]
  (assert (every? component? (first cs)) "Can only accept IComponents")
`(def ~(with-meta name (assoc (meta name) :doc ds)) 
   (Workflow. '~(first cs) {:description ~ds} nil))))
  

  

   
 
 
