package org.maurezen.indexer

import org.maurezen.indexer.impl.RichUserIndexEntry
import org.maurezen.indexer.impl.UserIndexEntry

/**
 * An snapshot of index builder state that the user can query.
 */
interface Index {

    /**
     * Returns a set of filenames that contain this query string.
     *
     * Doesn't rely on filesystem.
     */
    fun query(pattern: String): UserIndexEntry

    /**
     * Returns lines and line positions of the pattern occurrences for each file.
     *
     * May have to scan the affected files.
     */
    fun queryAndScan(pattern: String): RichUserIndexEntry

    /**
     * Stats like amount of ngrams, etc are pret
     */
    fun stats(): Stats
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

