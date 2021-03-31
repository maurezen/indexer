package org.maurezen.indexer.impl.naive

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import org.maurezen.indexer.*
import org.maurezen.indexer.impl.ACCEPTS_EVERYTHING
import org.maurezen.indexer.impl.FileReaderBasic
import org.maurezen.indexer.impl.NGram.Companion.reverseNgramsForFile
import org.maurezen.indexer.impl.inspection.YesMan
import org.maurezen.indexer.impl.mergeMapBitMap
import java.io.FileFilter
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

const val DEFAULT_NGRAM_ARITY = 3

open class IndexBuilderNaive (
    override val n: Int = DEFAULT_NGRAM_ARITY
) : IndexUpdater {

    @Volatile
    lateinit var currentIndex: Index

    val roots = ArrayList<String>()
    var filter = ACCEPTS_EVERYTHING
    var inspector: ContentInspector = YesMan
    var reader: FileReader = FileReaderBasic()

    override fun with(filename: String): IndexBuilder {
        roots.add(filename.intern())
        return this
    }

    override fun with(filenames: Iterable<String>): IndexBuilder {
        filenames.forEach(this::with)
        return this
    }

    override fun inspectedBy(inspector: ContentInspector): IndexBuilder {
        this.inspector = inspector
        return this
    }

    override fun readBy(reader: FileReader): IndexBuilder {
        this.reader = reader
        return this
    }

    override fun filter(filter: FileFilter): IndexBuilder {
        this.filter = filter
        return this
    }

    override fun buildAsync(): Deferred<Index> {
        return GlobalScope.async {
            buildFuture().get()
        }
    }

    override suspend fun buildFuture(): Future<Index> {
        currentIndex = if (roots.isNotEmpty()) {
            val filenames = reader.explodeFileRoots(roots)
            val fileMaps = filenames.mapIndexedTo(ArrayList()) { index, filename -> Pair(reverseNgramsForFile(filename,
                index,
                n,
                reader = reader), index) }

            val matches = fileMaps.removeAt(0).first
            fileMaps.fold(matches) { acc, entry -> acc.mergeMapBitMap(entry.first) }

            IndexNaive(n, matches, filenames, reader)
        } else {
            IndexNaive(n, hashMapOf(), arrayListOf(), reader)
        }

        return CompletableFuture.completedFuture(currentIndex)
    }

    override suspend fun update() {
        buildAsync().await()
    }

    override fun status(): State {
        return if (currentIndexInitialized()) State.READY else State.INITIAL
    }

    protected fun currentIndexInitialized() = this::currentIndex.isInitialized


    override suspend fun get(): Index {
        return currentIndex
    }

    override fun cancelUpdate() {
        //nothing to be done here, update is a synchronous call for a single-threaded implementation
    }

    /**
     * Returns true if and only if an update is in progress at the moment.
     */
    protected open fun updateInProgress(): Boolean = false

}


