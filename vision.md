### What
A basic (text) file indexer library in Kotlin. Given a character sequence, an indexer finds all the occurences of this sequence as a substring in a given set of files. Primary use case is indexing a moderately-sized codebase for subsequent searches.

### Feature set

 - Indexable files
    - [ ] A single file
    - [ ] A list of files     
 - Index building
    - [ ] Single-threaded
    - [ ] Multi-threaded, per-file parallelization
    - [ ] Multi-threaded, coroutine-based         
    - [ ] Emergency brake    
 - Index querying
    - [ ] Multi-threaded     
 - Correctness
    - [ ] functionally correct            
        - [ ] fuzzy test generator
    - [ ] infer setup to demonstrate lack of obvious issues
    - [ ] jcstress setup to demonstrate lack of obvious intermittent issues    
 - Performance
    - [ ] index building performance measurement setup, Mb/s
    - [ ] index query performance setup, qps & latency, parametrized by # of concurrent queries
    - [ ] index stats
    - [ ] trigrams vs 4-grams?     
    - [ ] map (line -> list (offset)) vs list (line, offset) 
        - as we're targeting codebases, strings are probably not that long
        - on the other hand, maps are much easier to intersect than list are
 - Nice-to-haves
    - Indexing
        - [ ] same-file parallelization
    - Querying
        - [ ] boolean expressions
        - [ ] case insensitive queries        
        - [ ] regular expressions
        - [ ] almost-matches (confidence score = percentage of ngrams matched)        
    - Files 
        - [ ] A list of files and/or directories and a list of exclusion rules allowing to ignore specific files/directories
        - [ ] File masks applicable to both include and exclude file sets
        - [ ] Codepage detection
        - [ ] Binary file detection    
        - [ ] File watcher to internalize th 
    - Incremental building
        - [ ] Able to quickly build an index for a fileset that has a few changes in relation to a fileset for a previously-built index 
    - Persistence
        - [ ] Able to serialize both per-file and overall index coupled with an unique identifier of a set of files
        - [ ] Able to deserialize index if the files haven't changed in between
        - [ ] Able to deserialize index and check whether any of the files has changed, updating the index for those that have                        
   
### How

A straightforward n-gram index implementation. 

Setup: specify the indexable fileset and the value of n (personally I tend to default to `n=3`, but it will have to be tested vs `n=4` at least)

Indexing: single pass over the indexable fileset, populating a naturally occuring data structure `map ngram -> map (file -> map (line -> list (offset)))`. This is trivially parallel on a per-file level; given the codebases tend to not have a single monstrous file (even 100k-line atrocities are not THAT big in the multi-gigabyte codebase), intra-file parallelization seems to be low priority. 

Querying: split the query string into ngrams, retrieve entries for each of the ngrams, produce an intersection of the resulting maps. Pretty straightforward to generalize to boolean expressions.