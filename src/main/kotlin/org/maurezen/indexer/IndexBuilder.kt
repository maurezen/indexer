package org.maurezen.indexer

import kotlinx.coroutines.Deferred
import org.maurezen.indexer.impl.ACCEPTS_EVERYTHING
import org.maurezen.indexer.impl.FileReaderBasic
import org.maurezen.indexer.impl.inspection.YesMan
import java.io.FileFilter
import java.util.concurrent.Future

/**
 * A builder facade for the index configuration and lifecycle management.
 *
 * NOT thread-safe, unlike actual index building process
 */
interface IndexBuilder {
    /**
     * Index arity. Default is n=3
     */
    val n: Int

    /**
     * Returns a currently available index.
     */
    fun get(): Index

    /**
     * Adds filename to this index content root set
     */
    fun with(filename: String): IndexBuilder

    /**
     * Adds filenames to this index content root set
     */
    fun with(filenames: Iterable<String>): IndexBuilder

    /**
     * Appoints a specific ContentInspector to be used during indexing.
     *
     * This instance of ContentInspector will be shared between all the worker threads involved in indexing,
     * plan accordingly.
     *
     * An instance of {@link YesMan} that returns true for each query is the default choice here.
     */
    fun inspectedBy(inspector: ContentInspector = YesMan): IndexBuilder

    /**
     * Appoints a specific FileReader to be used.
     *
     * An instance of {@link FileReaderBasic} is the default choice here.
     */
    fun readBy(reader: FileReader = FileReaderBasic()): IndexBuilder

    /**
     * Appoints a specific FileReader to be used.
     *
     * {@link ACCEPTS_EVERYTHING} that returns true for each query is the default choice here.
     */
    fun filter(filter: FileFilter = ACCEPTS_EVERYTHING): IndexBuilder

    /**
     * A deferred-based flavor of indexing.
     *
     * While indexing is in progress, a previously available index is returned on each query.
     *
     * To cancel indexing, call cancel on the deferred.
     */
    fun buildAsync(): Deferred<Index>

}