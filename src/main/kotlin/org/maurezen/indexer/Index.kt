package org.maurezen.indexer

import org.maurezen.indexer.impl.UserIndexEntry

interface Index {
    //@todo this is files only now that lines/positions are dropped. introduce an option to have lines/positions read from file(s).
    fun query(pattern: String): UserIndexEntry
    fun stats(): Stats
}

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
    val alphabet: String
)

