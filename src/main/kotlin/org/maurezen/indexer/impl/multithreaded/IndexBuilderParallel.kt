package org.maurezen.indexer.impl.multithreaded

import kotlinx.coroutines.*
import org.maurezen.indexer.Index
import org.maurezen.indexer.impl.NGram.Companion.reverseNgramsForFile
import org.maurezen.indexer.impl.mergeMapBitMap
import org.maurezen.indexer.impl.naive.DEFAULT_NGRAM_ARITY
import org.maurezen.indexer.impl.naive.IndexBuilderNaive
import org.maurezen.indexer.impl.naive.IndexNaive
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import java.util.stream.Collectors

open class IndexBuilderParallel (
    override val n: Int = DEFAULT_NGRAM_ARITY
) : IndexBuilderNaive(n) {

    @Volatile
    private lateinit var currentUpdate: CompletableFuture<Index>

    @Volatile
    @set:Synchronized
    //we don't expect much contention here; if we ever see it, a delegate will be introduced
    protected var updateNotInProgress: Boolean = true

    override fun get(): Index {
        return currentIndex
    }

    @Synchronized
    override fun buildAsync(): Deferred<Index> {
        return GlobalScope.async(Dispatchers.IO) {
            build().get()
        }
    }

    @Synchronized
    private fun build(): Future<Index> {
        if (updateNotInProgress) {
            currentUpdate = CompletableFuture.supplyAsync {
                if (roots.isNotEmpty()) {
                    val filenames = reader.explodeFileRoots(roots)

                    val fileMaps = filenames.mapIndexed { index, it -> Pair(index, it) }.parallelStream()
                        .map { pair -> reverseNgramsForFile(pair.second, pair.first, n, reader = reader) }
                        .collect(Collectors.toList())

                    val matches = fileMaps.parallelStream()
                        .reduce { acc, entry -> acc.mergeMapBitMap(entry) }
                        .orElse(hashMapOf())

                    currentIndex = IndexNaive(n, matches, filenames, reader)
                } else {
                    currentIndex = IndexNaive(n, hashMapOf(), arrayListOf(), reader)
                }

                updateNotInProgress = true
                currentIndex
            }
            updateNotInProgress = false
        }

        return currentUpdate
    }
}

