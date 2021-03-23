package org.maurezen.indexer.impl.coroutines

import kotlinx.coroutines.*
import org.maurezen.indexer.ContentInspector
import org.maurezen.indexer.Index
import org.maurezen.indexer.impl.*
import org.maurezen.indexer.impl.NGram.Companion.reverseNgramsForFile
import org.maurezen.indexer.impl.multithreaded.IndexBuilderParallel
import org.maurezen.indexer.impl.naive.IndexNaive
import java.util.concurrent.Future
import java.util.concurrent.FutureTask

class IndexBuilderCoroutines (
    override val n: Int
) : IndexBuilderParallel(n) {

    private val logger = logger()

    @Volatile
    private lateinit var currentUpdate: Deferred<Index>

    override fun cancelActually() {
        currentUpdate.cancel("User-requested cancellation")
    }

    @Synchronized
    override fun buildAsync(): Deferred<Index> {

        advanceStateToBuild()

        var matches: HashMap<String, IndexEntry>

        currentUpdate = GlobalScope.async {

            val filenames = explodeFileRoots(roots, filter)
            val fileMaps = reverseNGramsForFilesCoroutine(this, filenames, n, inspector)
            matches = coalesceReverseNgramsCoroutines(fileMaps)

            val newIndex = IndexNaive(n, matches, filenames)
            advanceStateFromBuild()
            currentIndex = newIndex
            currentIndex
        }

        return currentUpdate
    }

    private fun coalesceReverseNgrams(fileMaps: List<HashMap<String, IndexEntry>>) =
        fileMaps.parallelStream()
            .reduce { acc, entry -> acc.mergeMapBitMap(entry) }
            .orElse(hashMapOf())

    private suspend fun reverseNGramsForFilesCoroutine(scope: CoroutineScope, filenames: List<String>, n: Int, inspector: ContentInspector): List<HashMap<String, IndexEntry>> = run {
        val jobs = filenames.mapIndexed { index, it ->
            scope.async { reverseNgramsForFile(it, index, n, inspector) }
        }
        jobs.awaitAll()
    }

    private fun coalesceReverseNgramsCoroutines(fileMaps: List<HashMap<String, IndexEntry>>): HashMap<String, IndexEntry> {
        return coalesceReverseNgrams(fileMaps)
    }

    @Synchronized
    override suspend fun buildFuture(): Future<Index> {
        return FutureTask { runBlocking { buildAsync().await() } }
    }
}