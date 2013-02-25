(ns hotel_nlp.examples.workflows
   (:require  [hotel_nlp.protocols :refer :all]
              [hotel_nlp.externals.bindings :as bin]
              [hotel_nlp.concretions.models :refer :all]
              [hotel_nlp.concretions.artefacts :refer [reg-seg reg-tok stemmer]]
              [hotel_nlp.helper :as help]
              [hotel_nlp.core :refer [defcomponent defworkflow fn->component]]
              [clojure.pprint :refer [pprint print-table]]
   )
   (:import [hotel_nlp.concretions.models Workflow])
)
;;these come first
(bin/extend-opennlp :all)
(bin/extend-stanford-core)
(def sample "Any edit that changes content in a way that deliberately compromises the integrity of Wikipedia is considered vandalism. The most common and obvious types of vandalism include insertion of obscenities and crude humor. Vandalism can also include advertising language, and other types of spam. Sometimes editors commit vandalism by removing information or entirely blanking a given page. Less common types of vandalism, such as the deliberate addition of plausible but false information to an article, can be more difficult to detect. Vandals can introduce irrelevant formatting, modify page semantics such as the page's title or categorization, manipulate the underlying code of an article, or utilize images disruptively. Mr. Brown is dead after Michelle shot him!")  

(defcomponent opennlp-tok    "openNLP's simple tokenizer"    bin/opennlp-simple-tok)
(defcomponent opennlp-ssplit "openNLP's maxent sentence-splitter" (bin/opennlp-me-ssplit))
(defcomponent opennlp-pos  "openNLP's maxent pos-tagger"  (bin/opennlp-me-pos))                                                                    
(defcomponent opennlp-ner "openNLP's maxent ner [person]"   (bin/opennlp-me-ner))
(defcomponent opennlp-chunk "openNLP's maxent chunker"   (bin/opennlp-me-chunk))
(defcomponent opennlp-parse "openNLP's maxent chunker"   (bin/opennlp-me-parse))
(defcomponent my-ssplit "my own sentence-splitter" reg-seg)
(defcomponent my-tokenizer "my own sentence-splitter" reg-tok)
(defcomponent porter-stemmer "my own sentence-splitter" stemmer)


(defworkflow my-pipe "my own basic pipe" my-ssplit my-tokenizer porter-stemmer)
                                  
(defworkflow opennlp-basic-pipe "A common openNLP workflow."  
  opennlp-ssplit 
  opennlp-tok
  ;opennlp-parse 
  opennlp-pos 
  ;opennlp-ner
  ;opennlp-chunk  
 ) ;;a typical and common workflow
 
(defworkflow opennlp-parsing-pipe "A parsing openNLP workflow."  
  opennlp-ssplit 
  opennlp-tok
  opennlp-parse  
 )

(defworkflow opennlp-ner-pipe "A parsing openNLP workflow."  
  opennlp-ssplit 
  opennlp-tok
  opennlp-ner 
 ) 
 
(defworkflow mixed-pipe "a pipe with mixed components" my-ssplit my-tokenizer opennlp-pos)   

;;openNLP's chunker expects both tokens and pos-tags. This makes it slightly odd to use inside the workflow.  
;;nothing stops us to use it outside though. For example one can do this:
#_(let [redux (deploy opennlp-basic-pipe sample true)] ;;deploy the standard workflow first and ask for reductions
 (run opennlp-chunk   (nth redux 2)   ;the tokens
                      (nth redux 3))) ;the pos-tags 
                    
;;or this (nice demo of fn->component as well)

(defworkflow opennlp-chunking-pipe "A chunking openNLP workflow." 
                   (fn->component #(deploy opennlp-basic-pipe % true))
                   (fn->component #(zipmap (nth % 2)  (nth % 3))) 
                   (fn->component (fn [m] (run opennlp-chunk (keys m) (vals m)))))
;(deploy opennlp-chunking-pipe sample) 
                
  
(def stanford-pipe "A common stanford-nlp workflow."  ;;it is already a workflow - no need to use 'defworkflow'
  (bin/new-coreNLP (bin/new-properties "annotators" "tokenize" "ssplit" "pos" "lemma")))

;;deploy the 2 workflows in parallel 
(defn opennlp-vs-stanford [] 
(let [stanford-res (future (bin/squeeze-annotation (deploy stanford-pipe sample)))
      opennlp-res  (future (deploy opennlp-pipe sample))]
    {:opennlp  @opennlp-res 
     :stanford @stanford-res}  )  )

