package org.maurezen.indexer.impl

import org.maurezen.indexer.ContentInspector
import org.maurezen.indexer.FileReader
import org.maurezen.indexer.impl.inspection.YesMan
import java.lang.Exception

class NGram {
    companion object {
        private val logger = logger()

        /**
         * Splits a given string into an n-gram list.
         *
         * E.g. ngram("foobar", 3) -> ("foo", "oob", "oba", "bar")
         */
        fun ngram(string: String, n: Int): List<String> {
            //We have a sliding window, neat.
            return string.windowed(n)
        }

        /**
         * Splits a sequential list of strings (basically, a file) separated by a specified eol character (hello CR/CRLF/LF)
         * into an n-gram list.
         *
         * E.g. ngram(("foo", "bar", "baz"), 3, '\n') = ngram("foo\nbar\nbaz", 3)
         *
         * (While I fail to imagine a valid use case for a pattern containing an end of line symbol, this needs to be accounted for
         * to eliminate incorrect cross-line matches)
         */
        fun ngram(strings: List<String>, n: Int, eol: CharSequence = defaultEOL): List<String> {
            //@todo implement multiline windowed to produce a bit less garbage
            return ngram(strings.joinToString(separator = eol), n)
        }

        fun ngram(strings: Sequence<String>, n: Int, eol: CharSequence = defaultEOL): List<String> {
            //@todo joinToString is terminal, kinda undermines the whole idea
            return ngram(strings.joinToString(separator = eol), n)
        }

        fun ngramReverse(strings: Iterable<String>, n: Int, inspector: ContentInspector, filename: String, eol: CharSequence = defaultEOL): HashSet<String> {
            return ngramReverse(strings.asSequence(), n, inspector, filename, eol)
        }

        /**
         * Splits a list of strings into an ngram collection, being aware of line numbers in the process*
         */
        fun ngramReverse(strings: Sequence<String>, n: Int, inspector: ContentInspector, filename: String, eol: CharSequence = defaultEOL): HashSet<String> {
            val result = hashSetOf<String>()

            var line = 0

            //@todo joinToString is terminal, kinda undermines the whole idea.
            //we have join working on sequences, gotta implement windowed and we're golden
            strings.joinToString(eol).windowed(n).forEach { ngram: String ->
                val intern = ngram.intern()

                if (inspector.proceedOnNGram(intern, line, filename)) {
                    result.add(intern)

                    if (ngram.endsWith(eol)) {
                        line++
                    }
                } else {
                    //skipping the file completely if inspector tells us we're not going to proceed
                    logger.warn("Inspector complains about file $filename, skipping")
                    return hashSetOf()
                }
            }


            return result
        }

        /**
         * A convenience shortcut for when we don't care about different EOL symbols.
         */
        fun ngram(strings: List<String>, n: Int): List<String> {
            return ngram(strings, n, defaultEOL)
        }

        fun reverseNgramsForFile(
            filename: String,
            targetIndex: Int,
            n: Int,
            inspector: ContentInspector = YesMan,
            reader: FileReader
        ): HashMap<String, IndexEntry> {
            //map ngram -> bitmap (with a single) file
            val matches: HashMap<String, IndexEntry> = hashMapOf()

            if (inspector.proceedOnFile(filename)) {
                reader.readAnd(filename) { strings ->
                    val ngrams = ngramReverse(strings, n, inspector, filename)

                    for (ngram in ngrams) {
                        matches.computeIfAbsent(ngram) { IndexEntry() }
                        matches[ngram]!!.set(targetIndex)
                    }
                }
            } else {
                logger.warn("Undesirable file $filename detected, skipping")
            }
            //map ngram -> bitmap (with a single) file
            return matches
        }

    }
}