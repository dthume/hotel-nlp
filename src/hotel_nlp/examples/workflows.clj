(ns hotel_nlp.examples.workflows
   (:require  [hotel_nlp.protocols :refer :all]
              [hotel_nlp.externals.bindings :as bin]
              [hotel_nlp.concretions.artefacts :refer [reg-seg reg-tok stemmer brown-nltk-pos-probs abbreviation-extractor java-util-tokenizer+]]
              [hotel_nlp.algorithms.viterbi :as vit]
              [hotel_nlp.helper :as help]
              [hotel_nlp.core :refer [defcomponent defworkflow defgraph fn->component draw]]
              [clojure.pprint :refer [pprint print-table]]
              [clojure.java.io :as io]
              [opennlp.nlp :as clp]
              [opennlp.tools.filters :as fi]  
              [opennlp.treebank :as trb])
   (:import [hotel_nlp.helper GraphWorkflow] 
            [hotel_nlp.concretions.models HMM-POS-tagger])
)
(def ^java.util.Properties s-properties (System/getProperties)) ;just in case we need them

(.setProperty s-properties "WNSEARCHDIR", "/home/sorted/WordNet-3.0/dict/") ;better to do this here rather than in project.clj (I think!)
;(.setProperty s-properties "WNSEARCHDIR", "/home/dimitris/WordNet-3.0/dict/") 
(.setProperty s-properties "gate.home", "/home/sorted/gate-7.1-build4485-ALL/")
(.setProperty s-properties "gate.plugins.home", "/home/dimitris/gate-7.1-build4485-ALL/plugins/")
;;these should come first
(bin/extend-opennlp)
;(bin/extend-opennlp :modules [[:ner bin/spans->strings] [:chunk bin/chunk->spans]])
(bin/extend-stanford-core)
(bin/extend-gate)
;(bin/extend-gimli-ner)



(def bbc-sample ;;some bbc-sample sentences from the BBC news website
"Any edit that changes content in a way that deliberately compromises the integrity of Wikipedia is considered vandalism. The most common and obvious types of vandalism include insertion of obscenities and crude humor. Vandalism can also include advertising language, and other types of spam. Sometimes editors commit vandalism by removing information or entirely blanking a given page. Less common types of vandalism, such as the deliberate addition of plausible but false information to an article, can be more difficult to detect. Vandals can introduce irrelevant formatting, modify page semantics such as the page's title or categorization, manipulate the underlying code of an article, or utilize images disruptively. Mr. Brown is dead after someone shot him!") 
(def op-sample
"Pierre Vinken, 61 years old, will join the board as a nonexecutive director Nov. 29. Mr. Vinken is chairman of Elsevier N.V., the Dutch publishing group. Rudolph Agnew, 55 years old and former chairman of Consolidated Gold Fields PLC, was named a director of this British industrial conglomerate.")
(def easy-sample "Mary likes pizza but she also likes kebaps. Knowing her, I'd give it 2 weeks before she turns massive!")

(def bio-sample "BRCA1 and BRCA2 are human genes that belong to a class of genes known as tumor suppressors. Mutation of these genes has been linked to hereditary breast and ovarian cancer.")

(def sample ;;some sample sentences from the BBC news website
"Any edit that changes content in a way that deliberately compromises the integrity of Wikipedia is considered vandalism. The most common and obvious types of vandalism include insertion of obscenities and crude humor. Vandalism can also include advertising language, and other types of spam. Sometimes editors commit vandalism by removing information or entirely blanking a given page. Less common types of vandalism, such as the deliberate addition of plausible but false information to an article, can be more difficult to detect. Vandals can introduce irrelevant formatting, modify page semantics such as the page's title or categorization, manipulate the underlying code of an article, or utilize images disruptively. Mr. Brown is dead after someone shot him!")  

(defcomponent opennlp-tok    "openNLP's simple tokenizer"          bin/opennlp-simple-tok)
(defcomponent opennlp-ssplit "openNLP's maxent sentence-splitter" (bin/opennlp-me-ssplit))
(defcomponent opennlp-pos    "openNLP's maxent pos-tagger"        (bin/opennlp-me-pos))                                                                    
(defcomponent opennlp-ner    "openNLP's maxent ner [person]"      (bin/opennlp-me-ner))
(defcomponent opennlp-chunk  "openNLP's maxent chunker"           (bin/opennlp-me-chunk))
(defcomponent opennlp-parse  "openNLP's maxent parser"            (bin/opennlp-me-parse))
(defcomponent opennlp-coref  "openNLP's coreference linker"       (bin/opennlp-me-coref))
;(defcomponent opennlp-doccat  "openNLP's document-categorizer"   (bin/openlp-me-doccat )) 
(defcomponent my-ssplit      "my own sentence-splitter"           reg-seg) 
(defcomponent my-tokenizer   "my own tokenizer"                   reg-tok)
(defcomponent my-abbr-extractor "my own abbreviation extractor"   abbreviation-extractor) ;;supports both modes (abbreviation in paren & name in paren)
(defcomponent porter-stemmer  "porter's stemmer"                  stemmer)
(defcomponent my-pos-tagger   "my own HMM pos-tagger based on bigrams."
  (HMM-POS-tagger. (comp vit/proper-statistics vit/tables) vit/viterbi {:probs brown-nltk-pos-probs} nil)) ;;pass the pre-observed probabilities as meta-data
(defcomponent stanford-ssplit "stanford's sentence splitter"    (edu.stanford.nlp.pipeline.WordsToSentencesAnnotator. false))  
(defcomponent stanford-tok    "stanford's revertible tokenizer" (edu.stanford.nlp.pipeline.PTBTokenizerAnnotator. false))
;(defcomponent stanford-pos "stanford's maxent pos-tagger"     (edu.stanford.nlp.pipeline.POSTaggerAnnotator. false))  

#_(defcomponent gimli-bio-ner "the new gimli NER API" 
  (bin/gimli-Annotator 
   (bin/gimli-corpus pt.ua.tm.gimli.config.Constants$LabelFormat/BIO 
    pt.ua.tm.gimli.config.Constants$EntityType/protein))) 
    
    ;(run gimli-bio-ner (bin/gimli-crfmodel (pt.ua.tm.gimli.config.ModelConfig. "/home/sorted/GIMLI/bc.config") "/home/sorted/GIMLI/bc2gm_bw_o2.gz") bio-sample) 
    

(defworkflow my-stem-pipe "my own basic stemming pipe"     my-ssplit my-tokenizer porter-stemmer)
(defworkflow my-pos-pipe  "my own basic pos-tagging pipe"  my-ssplit my-tokenizer my-pos-tagger)
                                  
(defworkflow opennlp-basic-pipe  "A common openNLP workflow." 
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
  opennlp-parse)

(defworkflow opennlp-ner-pipe  "A NER openNLP workflow." 
  opennlp-ssplit 
  opennlp-tok
  opennlp-ner)
  
(comment   
 (deploy opennlp-ner-pipe op-sample true)
 (map bin/spans->strings 
     (last *1)           ;;the spans 
     (->   *1 butlast last)) ;;the tokens
)


; (run opennlp-coref (deploy opennlp-parsing-pipe op-sample) ["person" (deploy opennlp-ner-pipe op-sample) nil])
#_(doseq [e ents]
(loop [m (iterator-seq (.getMentions e))
       mstring "" ]
   (if (empty? m) (println "\tMention set:: [ " (str mstring) " ]" )
    (recur (rest m) 
      (if (= mstring "") (str (first m)) 
        (str mstring  " --> "  (str (first m))))))))
; (filter #(< 1 (.getNumMentions ^opennlp.tools.coref.DiscourseEntity %)) *1)
  
(defworkflow mixed-pipe1  "a pipe with mixed components"            my-ssplit my-tokenizer opennlp-pos) 
(defworkflow mixed-pipe2  "another pipe with mixed components-FAIL" my-ssplit stanford-tok opennlp-pos) ;;FAIL 
(defworkflow mixed-pipe3  "another pipe with mixed components-SUCC"  
 my-ssplit
 (fn->component (fn [sentences] (run stanford-tok 
                  (doto (edu.stanford.nlp.pipeline.Annotation. (apply str sentences)) 
                        (.set edu.stanford.nlp.ling.CoreAnnotations$SentencesAnnotation sentences))))) 
 (fn->component (fn [ann] (run opennlp-pos (-> ann bin/squeeze-annotation :tokens))))  )


;;openNLP's chunker expects both tokens and pos-tags. This makes it slightly odd to use inside the workflow.  
;;nothing stops us to use it outside though. For example one can do this:
#_(let [redux (deploy opennlp-basic-pipe easy-sample true)] ;;deploy the standard workflow first and ask for reductions
 (run opennlp-chunk   (nth redux 2)   ;the tokens
                      (nth redux 3))) ;the pos-tags 
                    
;;or this (nice demo of fn->component as well)
(defworkflow opennlp-chunking-pipe "A chunking openNLP workflow." 
                   (fn->component #(deploy mixed-pipe1 % true)) ;notice how now we're using mixed-pipe1 - no difference! 
                   (fn->component #(zipmap (nth % 2)  (nth % 3))) 

                   (fn->component #(reverse (run opennlp-chunk (keys %) (vals %)))) ) ;;reverse here due to zipping
;(deploy opennlp-chunking-pipe bbc-sample)

(defgraph opennlp-chunking-pipe++ "The same chunking pipe defined as a graph. The view for drawing is provided as an example too."  
  :SD  (help/component->fnk opennlp-ssplit s)  ;(fnk [s]   (run opennlp-ssplit s)) 
  :TOK (help/component->fnk opennlp-tok SD)    ;(fnk [SD]  (run opennlp-tok SD))
  :POS (help/component->fnk opennlp-pos TOK)   ;(fnk [TOK] (run opennlp-pos TOK))
  :CHU (help/component->fnk opennlp-chunk TOK POS)   ;(fnk [TOK POS] (run opennlp-chunk TOK POS)) 
   {opennlp-ssplit [opennlp-tok]
    opennlp-tok [opennlp-pos opennlp-chunk]
    opennlp-pos [opennlp-chunk]
    opennlp-chunk []} )
 
                
  
#_(def stanford-pipe "A common stanford-nlp workflow."  ;;it is already a workflow - no need to use 'defworkflow'
  (bin/new-coreNLP (bin/new-properties "annotators" "tokenize" "ssplit" "pos" "lemma" ))) ;"ner" "parse" "dcoref"
  
#_(bin/gate-init) ;;need this to initialise GATE 
#_(def gate-pipe "a pure GATE workflow" ;as with stanford - no need to use 'defworkflow'
 (let [pipe (gate.Factory/createResource "gate.creole.SerialAnalyserController")]
   (reduce #(doto % (.add (gate.Factory/createResource ^String %2))) pipe
            (into-array ["gate.creole.tokeniser.DefaultTokeniser" 
                         "gate.creole.splitter.SentenceSplitter"]))))
#_(.setDocument gate-pipe 
  (gate.Factory/newDocument (io/as-url (io/file "/home/sorted/clooJWorkspace/hotel-nlp/resources/corpora-train/spelling/dummy.txt"))))

#_(.setCorpus gate-pipe ;;we typically process a collection od documents -> a corpus
  (doto (gate.Factory/newCorpus "DUMMY-DOC!") 
    (.add (gate.Factory/newDocument (io/as-url (io/file "/home/sorted/clooJWorkspace/hotel-nlp/resources/corpora-train/spelling/dummy.txt"))))))                          

;;deploy the 2 workflows in parallel 
#_(defn opennlp-vs-stanford [] 
(let [stanford-res (future (bin/squeeze-annotation (deploy stanford-pipe bbc-sample)))
      opennlp-res  (future (deploy opennlp-basic-pipe bbc-sample))]
    {:opennlp  @opennlp-res 
     :stanford @stanford-res}  )  )
     
     
     
     

