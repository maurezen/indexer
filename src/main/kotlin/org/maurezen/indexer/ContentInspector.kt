package org.maurezen.indexer

/**
 * An extension point for running a custom heuristic on file contents.
 *
 * Answers the question "Do we want to work with this file?"
 * Whenever one of these methods returns false, the indexer skips the relevant file
 * and discards the results for it.
 *
 * This is relevant for any heuristic more complex than filename-based file filter.
 */
interface ContentInspector {

    /**
     * Is called once and only once for each filename in the indexer scope.
     * Is called before we have a chance to inspect any of the contents.
     *
     * This is a good moment to examine file size, last update time or other metadata.
     * This is NOT a good moment to perform a full scan on file contents.
     *
     * @return true if and only if we want to continue, false if the current file is to be skipped and the results
     *          obtained this far are to be discarded.
     */
    fun proceedOnFile(filename: String): Boolean

    /**
     * Is called once and only once for each ngram in the file.
     * On each call returning false means short-circuiting the current file and discarding its results.
     *
     * There is a single instance of content inspector per indexer instance; it will be called for different files
     * in an unpredictable sequence.
     *
     * At the moment there's no intra-file parallelization, but that doesn't mean that this method will be called
     * from the same thread for all the ngrams in a file due to possible coroutine migration. Plan accordingly.
     *
     * @return true if and only if we want to continue, false if the current file is to be skipped and the results
     *          obtained this far are to be discarded.
     */
    fun proceedOnNGram(ngram: String, line: Int, filename: String): Boolean

}