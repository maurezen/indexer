package org.maurezen.indexer.impl.naive

import org.maurezen.indexer.Index
import org.maurezen.indexer.Stats
import org.maurezen.indexer.impl.IndexEntry
import org.maurezen.indexer.impl.NGram.Companion.ngram
import org.maurezen.indexer.impl.UserIndexEntry
import org.maurezen.indexer.impl.intersectImmutable
import java.io.File

/**
 * A ready-to-query inverse ngram index over some filenames.
 * Doesn't really know about fileset that it was fed, can only serve queries.
 *
 * Is basically a read-only snapshot. As such, works in multithreaded environment just fine.
 */
class IndexNaive (
    private val n: Int,
    //map ngram -> bitmap file
    private val matches: HashMap<String, IndexEntry>,
    private val filenames: ArrayList<String>
) : Index {

    private val stats: Stats by lazy { buildStats()}

    override fun query(pattern: String): UserIndexEntry {
        val ngrams = ngram(pattern, n)

        val matchesPerNGram = ngrams.mapTo(ArrayList(ngrams.size)) { ngram -> matches.getOrDefault(ngram, IndexEntry())}

        return materialize(intersection(matchesPerNGram))
    }

    override fun stats(): Stats = stats

    private fun intersection(indexEntries: ArrayList<IndexEntry>): IndexEntry {
        return if (indexEntries.isEmpty()) {
            IndexEntry()
        } else {
            val intersection: IndexEntry = indexEntries.removeAt(0)

            indexEntries.fold(intersection) { acc, entry -> acc.intersectImmutable(entry) }
        }
    }

    private fun filename(index: Int) : String {
        return filenames[index]
    }

    private fun materialize(intersection: IndexEntry): UserIndexEntry {
        val result = UserIndexEntry()

        intersection.forEach { result.add(filename(it)) }

        return result
    }

    private fun buildStats(): Stats {
        val ngrams = matches.keys.size

        val filenames = filenames
        val filesQty = filenames.size
        val sizeTotal = filenames.map(::File).map(File::length).sum()
        val sizeMax = filenames.map(::File).map(File::length).maxOrNull() ?: 0L
        val sizeAvg = sizeTotal*1.0 / filesQty

        val linesMax = 42

        val entriesTotal = matches.values
            .flatten()
            .size
        val entriesPerNGramMax = matches.values
            .map(IndexEntry::cardinality)
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

fun UserIndexEntry.buildStats(): Stats {
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

