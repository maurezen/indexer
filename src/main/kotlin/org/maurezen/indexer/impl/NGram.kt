package org.maurezen.indexer.impl

import java.nio.file.Files
import java.nio.file.Path

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

        /**
         * Splits a list of strings into an ngram collection, being aware of line numbers in the process*
         */
        fun ngramReverse(
            strings: List<String>,
            n: Int,
            eol: CharSequence = defaultEOL,
            sniff: (String, Int) -> Unit = { _, _ -> }
        ): HashSet<String> {
            val result = hashSetOf<String>()

            var line = 0
            var offset = 0

            //@todo implement multiline windowed to produce a bit less garbage
            strings.joinToString(eol).windowed(n).forEach { ngram: String ->
                val intern = ngram.intern()
                //@todo circuit breaker when sniffer heuristics tell us we have a bad file
                sniff(intern, line)
                result.add(intern)

                offset++
                //at strings.size it points at exactly the line break, and we're pegging that as belonging to the same string
                //note that [line] is perfectly valid because windowed ends at last full ngram
                if (offset > strings[line].length) {
                    offset = 0
                    line++
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
            sniff: (String, String, Int) -> Unit = { _, _, _ -> }
        ): HashMap<String, IndexEntry> {
            //map ngram -> bitmap (with a single) file
            val matches: HashMap<String, IndexEntry> = hashMapOf()

            if (wannaProcessThat(filename)) {
                val strings = read(filename)

                val ngrams = ngramReverse(strings, n, sniff = { s, line -> sniff(s, filename, line) })

                for (ngram in ngrams) {
                    matches.computeIfAbsent(ngram) { IndexEntry() }
                    matches[ngram]!!.set(targetIndex)
                }
            } else {
                logger.warn("Undesirable file $filename detected, skipping")
            }
            //map ngram -> bitmap (with a single) file
            return matches
        }


        val types = HashSet<String>()
        fun wannaProcessThat(filename: String): Boolean {
            val contentType = Files.probeContentType(Path.of(filename))

            //  .java is text/plain
            //  .kt is null
            //  .zip is application/whatever
            if (types.add(contentType)) {
                logger.warnIfEnabled { "First time detected $contentType type: $filename" }
            }
            return true
        }
    }
}