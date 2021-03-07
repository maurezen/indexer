package org.maurezen.indexer

interface IndexUpdater: IndexBuilder {
    suspend fun get(): Index
    suspend fun update()
    fun status(): State
    fun cancelUpdate()
    fun updateInProgress(): Boolean
}