package org.maurezen.indexer

interface FileReader {

    /**
     * Returns a sequence of lines of this file without pulling the whole thing into memory.
     */
    fun read(filename: String): Sequence<String>

    /**
     * Pulls the whole file into memory.
     */
    fun readAsList(filename: String): List<String>

    fun readAsMap(filenames: List<String>): Map<String, List<String>> = filenames.associateWith(::readAsList)
}

