package org.maurezen.indexer.impl.inspection

import org.maurezen.indexer.ContentInspector
import org.maurezen.indexer.impl.alphabetExtended
import org.maurezen.indexer.impl.logger
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * An inspector that only allows at most tolerance characters not on the whitelist
 */
class WhitelistCharacterInspector(
    private val tolerance: Int,
    charWhitelist: String = alphabetExtended
): ContentInspector {

    private val logger = logger()

    private val types = HashSet<String>()
    private val whitelistSet = charWhitelist.toHashSet()
    private val suspiciousChars = ConcurrentHashMap<String, HashSet<Char>>()

    override fun proceedOnFile(filename: String): Boolean {
        val contentType = Files.probeContentType(Path.of(filename))

        //  .java is text/plain
        //  .kt is null
        //  .zip is application/whatever
        if (types.add(contentType)) {
            logger.warn("First time detected $contentType type: $filename")
        }
        return true
    }

    override fun proceedOnNGram(ngram: String, line: Int, filename: String): Boolean {
        ngram.toCharArray()
            .filter { !whitelistSet.contains(it) }
            .forEach {
                suspiciousChars.computeIfAbsent(filename) { hashSetOf() }
                if (suspiciousChars[filename]!!.add(it)) {
                    logger.warn("First occurrence of suspicious char \"$it\" - happened in $filename at line $line")
                }
            }
        return suspiciousChars[filename]?.size ?: 0 <= tolerance
    }
}