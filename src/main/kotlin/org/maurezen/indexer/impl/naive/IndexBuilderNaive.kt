package org.maurezen.indexer.impl.naive

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import org.maurezen.indexer.*
import org.maurezen.indexer.impl.ACCEPTS_EVERYTHING
import org.maurezen.indexer.impl.FileReaderBasic
import org.maurezen.indexer.impl.NGram.Companion.reverseNgramsForFile
import org.maurezen.indexer.impl.inspection.YesMan
import org.maurezen.indexer.impl.mergeMapBitMap
import java.io.FileFilter

const val DEFAULT_NGRAM_ARITY = 3

open class IndexBuilderNaive (
    override val n: Int = DEFAULT_NGRAM_ARITY
) : IndexBuilder {

    @Volatile
    var currentIndex: Index = EmptyIndex

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
        return CompletableDeferred(build())
    }

    private fun build(): Index {
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

        return currentIndex
    }

    protected fun currentIndexInitialized() = currentIndex != EmptyIndex

    override fun get(): Index {
        return currentIndex
    }

}


