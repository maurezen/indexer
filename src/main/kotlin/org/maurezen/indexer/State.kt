package org.maurezen.indexer

/**
 * A state of the index builder
 */
enum class State {
    /**
     * The builder has just been created and not even the first build process has started
     */
    INITIAL,

    /**
     * An indexing process is underway
     */
    BUILD,

    /**
     * There's an index snapshot built and ready to be queried
     */
    READY,

    /**
     * Something went wrong
     */
    ACHTUNG;
}