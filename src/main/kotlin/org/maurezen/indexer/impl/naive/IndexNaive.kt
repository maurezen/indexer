package org.maurezen.indexer.impl.naive

import org.maurezen.indexer.FileReader
import org.maurezen.indexer.Index
import org.maurezen.indexer.Stats
import org.maurezen.indexer.impl.*
import org.maurezen.indexer.impl.NGram.Companion.ngram
import java.io.File

/**
 * A ready-to-query inverse ngram index over some filenames.
 *
 * Is basically a read-only snapshot. As such, works in multithreaded environment just fine.
 */
class IndexNaive (
    private val n: Int,
    //map ngram -> bitmap file
    private val matches: HashMap<String, IndexEntryInternal>,
    private val filenames: ArrayList<String>,
    private val reader: FileReader
) : Index {

    private val stats: Stats by lazy { buildStats()}

    override fun query(pattern: String): IndexEntry {
        val ngrams = ngram(pattern, n)

        val matchesPerNGram = ngrams.mapTo(ArrayList(ngrams.size)) { ngram -> matches.getOrDefault(ngram, IndexEntryInternal())}

        return materialize(intersection(matchesPerNGram))
    }

    override fun queryAndScan(pattern: String): RichIndexEntry {
        return enrich(query(pattern), pattern)
    }

    override fun stats(): Stats = stats

    private fun intersection(indexEntries: ArrayList<IndexEntryInternal>): IndexEntryInternal {
        return if (indexEntries.isEmpty()) {
            IndexEntryInternal()
        } else {
            val intersection: IndexEntryInternal = indexEntries.removeAt(0)

            indexEntries.fold(intersection) { acc, entry -> acc.intersectImmutable(entry) }
        }
    }

    private fun filename(index: Int) : String {
        return filenames[index]
    }

    private fun materialize(intersection: IndexEntryInternal): IndexEntry {
        val result = IndexEntry()

        intersection.forEach { result.add(filename(it)) }

        return result
    }

    private fun enrich(intersection: IndexEntry, query: String): RichIndexEntry {
        val result = RichIndexEntry()

        intersection.forEach { result[it] = scan(it, query) }

        return result
    }

    private fun scan(filename: String, query: String, eol: String = defaultEOL): Map<Int, List<Int>> {
        return getFileContentsAnd(filename) { it.join(eol).indicesOf(query) }
    }

    @Synchronized
    //TODO make file scans multithreaded-environment-friendly
    //TODO cache file scans
    private fun <T> getFileContentsAnd(filename: String, block: (Sequence<String>) -> T) = reader.readAnd(filename, block)

    private fun buildStats(): Stats {
        val ngrams = matches.keys.size

        val filenames = filenames
        val filesQty = filenames.size
        val sizeTotal = filenames.map(::File).map(File::length).sum()
        val sizeMax = filenames.map(::File).map(File::length).maxOrNull() ?: 0L
        val sizeAvg = sizeTotal*1.0 / filesQty

        val linesMax = 42

        val entriesTotal = matches.values.map(IndexEntryInternal::cardinality)
            .sum()
        val entriesPerNGramMax = matches.values
            .map(IndexEntryInternal::cardinality)
            .maxOrNull() ?: 0
        val entriesPerNGramAvg = entriesTotal*1.0 / ngrams

        val avgNGramsPerFile = 0.0

        val alphabetList = matches.keys.flatMap { str -> val list = mutableListOf<Char>(); str.forEach { ch -> list.add(ch) }; list }.distinct()
        val alphabet = alphabetList.sorted().joinToString("")

        return Stats(
            ngrams,
            filesQty, sizeMax, sizeTotal, sizeAvg, linesMax, entriesTotal, entriesPerNGramMax, entriesPerNGramAvg, avgNGramsPerFile, alphabet
        )
    }

    override fun toString(): String {
        return "IndexNaive(n=$n, stats=$stats, matches=\n$matches\n)"
    }
}

fun IndexEntry.buildStats(): Stats {
    val ngrams = 1

    val filesQty = size
    val filenames = this
    val sizeTotal = filenames.map(::File).map(File::length).sum()
    val sizeMax = filenames.map(::File).map(File::length).maxOrNull() ?: 0L
    val sizeAvg = sizeTotal*1.0 / filesQty

    val linesMax = 42

    val entriesTotal = size
    val entriesPerNGramMax = size
    val entriesPerNGramAvg = entriesTotal*1.0 / ngrams

    val avgNGramsPerFile = 0.0

    return Stats(
        ngrams,
        filesQty, sizeMax, sizeTotal, sizeAvg, linesMax, entriesTotal, entriesPerNGramMax, entriesPerNGramAvg, avgNGramsPerFile, ""
    )
}

fun RichIndexEntry.buildStats(): Stats {
    val ngrams = 1

    val filesQty = size
    val filenames = this.keys
    val sizeTotal = filenames.map(::File).map(File::length).sum()
    val sizeMax = filenames.map(::File).map(File::length).maxOrNull() ?: 0L
    val sizeAvg = sizeTotal*1.0 / filesQty

    val linesMax = 42

    val entriesTotal = size
    val entriesPerNGramMax = size
    val entriesPerNGramAvg = entriesTotal*1.0 / ngrams

    val avgNGramsPerFile = 0.0

    return Stats(
        ngrams,
        filesQty, sizeMax, sizeTotal, sizeAvg, linesMax, entriesTotal, entriesPerNGramMax, entriesPerNGramAvg, avgNGramsPerFile, ""
    )
}

