package org.maurezen.indexer

import org.maurezen.indexer.impl.ACCEPTS_EVERYTHING
import java.io.FileFilter

interface FileReader {

    /**
     * Returns a sequence of lines of this file without pulling the whole thing into memory.
     */
    fun <T> readAnd(filename: String, block: (Sequence<String>) -> T): T

    /**
     * Pulls the whole file into memory.
     */
    fun readAsList(filename: String): List<String> = readAnd(filename) { it.toList() }

    fun <T> readAnd(filenames: List<String>, block: (Sequence<String>) -> T): Map<String, T> = filenames.associateWith { readAnd(it, block) }

    /**
     * Returns a list of filesystem leaves obtained by walking the filesystem starting at the provided roots
     */
    fun explodeFileRoots(roots: List<String>, filter: FileFilter = ACCEPTS_EVERYTHING): ArrayList<String>
}

