package org.maurezen.indexer.impl.multithreaded

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import org.maurezen.indexer.Index
import org.maurezen.indexer.State
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
    var state: State = State.INITIAL

    override suspend fun get(): Index {
        return currentIndex
    }

    override suspend fun update() {
        buildAsync()
    }

    override fun status(): State {
        return state
    }

    @Synchronized
    override fun cancelUpdate() {
        if (updateInProgress()) {
            cancelActually()
            state = (if (currentIndexInitialized()) State.READY else State.INITIAL)
        }
    }

    @Synchronized
    protected open fun cancelActually() {
        currentUpdate.cancel(true)
    }

    @Synchronized
    fun advanceStateToBuild() {
        if (updateInProgress()) {
            throw UnsupportedOperationException("Indexer doesn't support concurrent updates. Please ")
        }

        state = State.BUILD
    }

    @Synchronized
    fun advanceStateFromBuild() {
        if (State.BUILD == state) {
            state = State.READY
        } else {
            state = State.ACHTUNG
            throw IllegalStateException("Something changed the state while index was being built. This is an indexer bug or an outside meddling; either way, restore a saved game to restore the weave of fate, or persist in the doomed world that was created")
        }
    }

    @Synchronized
    override fun updateInProgress() =
        State.BUILD == state

    @Synchronized
    override fun buildAsync(): Deferred<Index> {
        return GlobalScope.async {
            buildFuture().get()
        }
    }

    @Synchronized
    override suspend fun buildFuture(): Future<Index> {
        advanceStateToBuild()

        currentUpdate = CompletableFuture.supplyAsync {
            if (roots.isNotEmpty()) {
                val filenames = reader.explodeFileRoots(roots)

                val fileMaps = filenames.mapIndexed {index, it -> Pair(index, it)}.parallelStream()
                    .map { pair -> reverseNgramsForFile(pair.second, pair.first, n, reader = reader) }
                    .collect(Collectors.toList())

                val matches = fileMaps.parallelStream()
                    .reduce { acc, entry -> acc.mergeMapBitMap(entry) }
                    .orElse(hashMapOf())

                advanceStateFromBuild()

                currentIndex = IndexNaive(n, matches, filenames, reader)
            } else {
                currentIndex = IndexNaive(n, hashMapOf(), arrayListOf(), reader)
            }

            currentIndex
        }

        return currentUpdate
    }
}

