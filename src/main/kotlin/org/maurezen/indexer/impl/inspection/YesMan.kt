package org.maurezen.indexer.impl.inspection

import org.maurezen.indexer.ContentInspector

object YesMan: ContentInspector {

    override fun proceedOnFile(filename: String): Boolean {
        return true
    }

    override fun proceedOnNGram(ngram: String, line: Int, filename: String): Boolean {
        return true
    }
}