package org.maurezen.indexer

import org.maurezen.indexer.impl.RichUserIndexEntry
import org.maurezen.indexer.impl.UserIndexEntry

interface Index {

    fun query(pattern: String): UserIndexEntry
    fun queryAndScan(pattern: String): RichUserIndexEntry

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

