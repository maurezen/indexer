package org.maurezen.indexer.impl.naive

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import org.maurezen.indexer.*
import org.maurezen.indexer.impl.ACCEPTS_EVERYTHING
import org.maurezen.indexer.impl.NGram.Companion.reverseNgramsForFile
import org.maurezen.indexer.impl.explodeFileRoots
import org.maurezen.indexer.impl.mergeMapBitMap
import java.io.FileFilter
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

open class IndexBuilderNaive (
    override val n: Int
) : IndexUpdater {

    @Volatile
    lateinit var currentIndex: Index

    val roots = ArrayList<String>()
    var filter = ACCEPTS_EVERYTHING
    var inspector: ContentInspector = YesMan

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

    override fun filter(filter: FileFilter): IndexBuilder {
        this.filter = filter
        return this
    }

    //@todo this kinda screws the whole concept over innit
    override fun buildAsync(): Deferred<Index> {
        return GlobalScope.async {
            buildFuture().get()
        }
    }

    override suspend fun buildFuture(): Future<Index> {
        currentIndex = if (roots.isNotEmpty()) {
            val filenames = explodeFileRoots(roots)
            val fileMaps = filenames.mapIndexedTo(ArrayList()) { index, filename -> Pair(reverseNgramsForFile(filename, index, n), index) }

            val matches = fileMaps.removeAt(0).first
            fileMaps.fold(matches) { acc, entry -> acc.mergeMapBitMap(entry.first) }

            IndexNaive(n, matches, filenames)
        } else {
            IndexNaive(n, hashMapOf(), arrayListOf())
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

    override fun updateInProgress(): Boolean = false


}


