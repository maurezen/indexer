package org.maurezen.indexer

interface ContentInspector {

    /**
     * @return true if and only if we want to continue, false if the current file is to be skipped
     */
    fun proceedOnFile(filename: String): Boolean

    /**
     * @return true if and only if we want to continue, false if the current file is to be skipped
     */
    fun proceedOnNGram(ngram: String, line: Int, filename: String): Boolean

}

val YesMan = object: ContentInspector {
    override fun proceedOnFile(filename: String): Boolean {
        return true
    }

    override fun proceedOnNGram(ngram: String, line: Int, filename: String): Boolean {
        return true
    }
}