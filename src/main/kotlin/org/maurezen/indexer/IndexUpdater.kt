package org.maurezen.indexer

/**
 * Lifecycle management part of {@link IndexBuilder}
 */
interface IndexUpdater: IndexBuilder {

    /**
     * Returns a currently available index.
     */
    suspend fun get(): Index

    /**
     * Requests an index refresh. A new index is computed for the existing content roots.
     */
    suspend fun update()

    fun status(): State

    /**
     * Cancels an ongoing update. Whatever state the index was in pre-update remains available.
     */
    fun cancelUpdate()
}