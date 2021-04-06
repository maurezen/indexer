package org.maurezen.indexer.impl.coroutines

import kotlinx.coroutines.*
import org.maurezen.indexer.Index
import org.maurezen.indexer.impl.*
import org.maurezen.indexer.impl.NGram.Companion.reverseNgramsForFile
import org.maurezen.indexer.impl.multithreaded.IndexBuilderParallel
import org.maurezen.indexer.impl.naive.DEFAULT_NGRAM_ARITY
import org.maurezen.indexer.impl.naive.IndexNaive

class IndexBuilderCoroutines (
    override val n: Int = DEFAULT_NGRAM_ARITY
) : IndexBuilderParallel(n) {

    @Volatile
    private lateinit var currentUpdate: Deferred<Index>

    @Synchronized
    override fun buildAsync(): Deferred<Index> {
        if (updateNotInProgress) {
            var matches: HashMap<String, IndexEntryInternal>

            currentUpdate = GlobalScope.async {

                val filenames = explodeFileRoots(roots, filter)
                val fileMaps = reverseNGramsForFilesCoroutine(this, filenames)
                matches = coalesceReverseNgrams(fileMaps)

                val newIndex = IndexNaive(n, matches, filenames, reader)

                updateNotInProgress = true
                currentIndex = newIndex
                currentIndex
            }
            updateNotInProgress = false
        }
        return currentUpdate
    }

    private fun coalesceReverseNgrams(fileMaps: List<HashMap<String, IndexEntryInternal>>) =
        fileMaps.parallelStream()
            .reduce { acc, entry -> acc.mergeMapBitMap(entry) }
            .orElse(hashMapOf())

    private suspend fun reverseNGramsForFilesCoroutine(scope: CoroutineScope, filenames: List<String>): List<HashMap<String, IndexEntryInternal>> = run {
        val jobs = filenames.mapIndexed { index, it ->
            scope.async { reverseNgramsForFile(it, index, n, inspector, reader) }
        }
        jobs.awaitAll()
    }

}