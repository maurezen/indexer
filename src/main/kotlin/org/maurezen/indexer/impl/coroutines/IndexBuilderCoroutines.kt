package org.maurezen.indexer.impl.coroutines

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.maurezen.indexer.Index
import org.maurezen.indexer.impl.IndexEntryInternal
import org.maurezen.indexer.impl.NGram.Companion.reverseNgramsForFile
import org.maurezen.indexer.impl.mergeMapBitMap
import org.maurezen.indexer.impl.multithreaded.IndexBuilderParallel
import org.maurezen.indexer.impl.naive.DEFAULT_NGRAM_ARITY
import org.maurezen.indexer.impl.naive.IndexNaive

/**
 * Coroutines-based parallel implementation of the indexer.
 *
 * For the time being uses a single scope both for readers and mergers; defaults to GlobalScope if none is passed.
 *
 * Readers & mergers allow for throughput<------>memory footprint tuning.
 *
 * Please see readme.md for throughput/footprint sample(s).
 */
class IndexBuilderCoroutines (
    override val n: Int = DEFAULT_NGRAM_ARITY,
    private val scope: CoroutineScope = GlobalScope,
    private val readers: Int = 256,
    private val mergers: Int = 64,
    private val filenameChannelCapacity: Int = 1024,
    private val readerChannelCapacity: Int = 1024
) : IndexBuilderParallel(n) {

    @Volatile
    private lateinit var currentUpdate: Deferred<Index>

    @Synchronized
    override fun buildAsync(): Deferred<Index> {
        if (updateNotInProgress) {
            var matches: HashMap<String, IndexEntryInternal>

            currentUpdate = scope.async {

                val filenames = reader.explodeFileRoots(roots, filter)

                matches = reverseNGramsForFilesAndCoalesce(this, filenames)

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

    private suspend fun reverseNGramsForFilesAndCoalesce(scope: CoroutineScope, filenames: List<String>): HashMap<String, IndexEntryInternal> = run {
        val filenameChannel = Channel<Pair<Int, String>>(filenameChannelCapacity)
        val readerChannel = Channel<HashMap<String, IndexEntryInternal>>(readerChannelCapacity)

        val fileNameJob = scope.async {
            filenames.mapIndexed{ index, it -> filenameChannel.send(Pair(index, it))}
        }

        val readerJobs = (1..readers).map {
            scope.async {
                for ((index, filename) in filenameChannel) {
                    val fileIndex = reverseNgramsForFile(filename, index, n, inspector, reader)
                    readerChannel.send(fileIndex)
                }
            }
        }

        val mergeJobs =
            (1..mergers).map {
                scope.async {

                    var mergedIndex: HashMap<String, IndexEntryInternal> = hashMapOf()

                    for (fileIndex in readerChannel) {
                        if (mergedIndex.isEmpty()) {
                            mergedIndex = fileIndex
                        } else {
                            mergedIndex.mergeMapBitMap(fileIndex)
                        }
                    }

                    mergedIndex
                }
            }

        fileNameJob.await()
        filenameChannel.close()
        readerJobs.awaitAll()
        readerChannel.close()

        return coalesceReverseNgrams(mergeJobs.awaitAll())
    }

}