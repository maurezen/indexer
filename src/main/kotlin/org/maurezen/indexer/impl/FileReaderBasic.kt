package org.maurezen.indexer.impl

import arrow.core.Either
import org.maurezen.indexer.FileReader
import java.io.File
import java.io.FileFilter
import java.io.FileNotFoundException

// @TODO CRLF files read at LF or CR systems and vice versa
val defaultEOL: String = System.lineSeparator()

val ACCEPTS_EVERYTHING = FileFilter { true }

class FileReaderBasic: FileReader {

    private val logger = logger()

    /**
     * Returns a sequence of lines of this file without pulling the whole thing into memory. Assumes the file exists.
     * For the moment assumes UTF-8
     */
    override fun <T> readAnd(filename: String, block: (Sequence<String>) -> T): Either<Unit, T> {
        return try {
            Either.Right(File(filename).useLines { block(it) })
        } catch (e: FileNotFoundException) {
            logger.error("Couldn't open file $filename, skipping")
            logger.debug("Couldn't open file $filename, skipping", e)
            Either.Left(Unit)
        }
    }

    override fun explodeFileRoots(roots: List<String>, filter: FileFilter): ArrayList<String> {
        val files = arrayListOf<String>()
        val filterOrDirectory = FileFilter { file -> (file != null && file.isDirectory) || filter.accept(file) }
        val fileQueue = ArrayDeque<String>()
        fileQueue.addAll(roots)
        //@todo handle symlinks (and use Files.walk in general)
        while (!fileQueue.isEmpty()) {
            val file = File(fileQueue.removeFirst())

            if (file.isDirectory) {
                val list = file.listFiles(filterOrDirectory)
                if (list != null) {
                    fileQueue.addAll(list.map(File::getAbsolutePath))
                } else {
                    logger.error("Got `null` instead of directory listing for $file, skipping")
                }
            } else if (filterOrDirectory.accept(file)) {
                files.add(file.absolutePath.intern())
            }
        }

        return files
    }
}