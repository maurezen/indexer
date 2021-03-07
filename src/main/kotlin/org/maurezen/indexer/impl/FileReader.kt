package org.maurezen.indexer.impl

import java.io.File
import java.io.FileFilter

// @TODO CRLF files read at LF or CR systems and vice versa
val defaultEOL: CharSequence = "\n"

val ACCEPTS_EVERYTHING = FileFilter { true }

/**
 * Pulls the whole file into memory. Assumes the file exists.
 * For the moment assumes UTF-8.
 */
fun read(filename: String): List<String> = File(filename).useLines { it.toList() }

fun read(filenames: List<String>): Map<String, List<String>> = filenames.associateWith(::read)

fun explodeFileRoots(roots: List<String>, filter: FileFilter = ACCEPTS_EVERYTHING): ArrayList<String> {
    val files = arrayListOf<String>()
    val filterOrDirectory = FileFilter { file -> (file != null && file.isDirectory) || filter.accept(file) }
    val fileQueue = ArrayDeque<String>()
    fileQueue.addAll(roots)
    //@todo handle symlinks (and use Files.walk in general)
    while (!fileQueue.isEmpty()) {
        val file = File(fileQueue.removeFirst())

        if (file.isDirectory) {
            fileQueue.addAll(file.listFiles(filterOrDirectory).map(File::getAbsolutePath))
        } else if (filterOrDirectory.accept(file)) {
            files.add(file.absolutePath.intern())
        }
    }

    return files
}