package org.maurezen.indexer

import org.maurezen.indexer.impl.RichIndexEntry
import org.maurezen.indexer.impl.IndexEntry

/**
 * An snapshot of index builder state that the user can query.
 */
interface Index {

    /**
     * Returns a set of filenames that contain this query string.
     *
     * Doesn't rely on filesystem.
     */
    fun query(pattern: String): IndexEntry

    /**
     * Returns lines and line positions of the pattern occurrences for each file.
     *
     * May have to scan the affected files.
     */
    fun queryAndScan(pattern: String): RichIndexEntry

    /**
     * Stats like amount of ngrams, etc
     */
    fun stats(): Stats
}

/**
 * A null object for the Index, this is something that one gets when trying to get an index before the indexing process is finished
 */
object EmptyIndex: Index {
    override fun query(pattern: String): IndexEntry {
        return hashSetOf()
    }

    override fun queryAndScan(pattern: String): RichIndexEntry {
        return hashMapOf()
    }

    override fun stats(): Stats {
        return Stats(0, 0, 0, 0, 0.0, 0, 0, 0, 0.0, 0.0, "")
    }
}

/**
 * Some possibly useful metrics of the index internal state.
 */
data class Stats(
    val ngrams: Int,
    val files: Int,
    val fileSizeMax: Long,
    val fileSizeTotal: Long,
    val fileSizeAvg: Double,
    val linesPerFileMax: Int,
    val entriesTotal: Int,
    val entriesPerNGramMax: Int,
    val entriesPerNGramAvg: Double,
    val avgNGramsPerFile: Double,
    /**
     * All the different symbols occurring in the files this index has indexed
     */
    val alphabet: String
)

