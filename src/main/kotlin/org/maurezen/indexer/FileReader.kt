package org.maurezen.indexer

/**
 * An extension point to obtain file contents in a non-standard way
 */
interface FileReader {

    /**
     * Returns a sequence of lines of this file without pulling the whole thing into memory.
     *
     */
    fun read(filename: String): Sequence<String>

    /**
     * Returns a list of lines of this file. Pulls the whole file into memory.
     */
    fun readAsList(filename: String): List<String>

    /**
     * Reads and returns the contents of a few files. Pulls everything into memory.
     */
    fun readAsMap(filenames: List<String>): Map<String, List<String>> = filenames.associateWith(::readAsList)
}

