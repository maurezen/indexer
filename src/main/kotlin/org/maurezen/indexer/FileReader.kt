package org.maurezen.indexer

import arrow.core.Either
import org.maurezen.indexer.impl.ACCEPTS_EVERYTHING
import org.maurezen.indexer.impl.associateWithNotNull
import java.io.FileFilter
import java.lang.Exception

/**
 * An extension point to obtain file contents in a non-standard way
 */
interface FileReader {

    /**
     * Returns an application of a supplied code block to the  sequence of lines of this file
     * without pulling the whole thing into memory.
     */
    fun <T> readAnd(filename: String, block: (Sequence<String>) -> T): Either<Unit, T>

    /**
     * Returns a list of lines of this file. Pulls the whole file into memory.
     */
    fun readAsList(filename: String): Either<Unit, List<String>> = readAnd(filename) { it.toList() }

    /**
     * Returns a map of filenames to the result of application of a supplied block to the filename in question.
     */
    fun <T> readAnd(filenames: List<String>, block: (Sequence<String>) -> T): Map<String, T> {
        return filenames.associateWithNotNull { readAnd(it, block).orNull() }
    }

    /**
     * Returns a list of filesystem leaves obtained by walking the filesystem starting at the provided roots
     */
    fun explodeFileRoots(roots: List<String>, filter: FileFilter = ACCEPTS_EVERYTHING): ArrayList<String>
}

