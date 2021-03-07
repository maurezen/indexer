package org.maurezen.indexer

import kotlinx.coroutines.Deferred
import java.io.FileFilter
import java.util.concurrent.Future

/**
 * A builder facade for the index configuration
 *
 * NOT thread-safe, unlike actual index building process in some implementations
 */
interface IndexBuilder {
    val n: Int

    fun with(filename: String): IndexBuilder
    fun with(filenames: Iterable<String>): IndexBuilder
    fun filter(filter: FileFilter): IndexBuilder

    fun buildAsync(): Deferred<Index>
    suspend fun buildFuture(): Future<Index>
}