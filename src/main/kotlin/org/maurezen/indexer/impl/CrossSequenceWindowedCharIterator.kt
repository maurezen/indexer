package org.maurezen.indexer.impl

/**
 * (See Sequence#windowedChars @ Helperz)
 *
 * strings.joinToString(separator).windowed(n) gives the same sequence of sliding windows
 * as strings.join(separator).windowedChars(n) does without pulling the whole sequence in memory.
 *
 * This iterator over a sequence of strings is what makes that possible.
 *
 * Overall, this is a hot mess, but it is tested both on random sequences and idea sources.
 *
 * Is absolutely NOT THREAD SAFE.
 */
class CrossSequenceWindowedCharIterator(
    private val size: Int,
    private val step: Int,
    private val strings: Iterator<String>
): Iterator<String> {

    private var currentString = if (strings.hasNext()) strings.next() else ""
    private var currentStringPosition = 0
    private var symbolsRemaining = currentString.length
    private val upcomingStrings = mutableListOf<String>()

    override fun hasNext(): Boolean {
        grabMoreStringsIfNeeded()
        return symbolsRemaining >= size
    }

    override fun next(): String {

        grabMoreStringsIfNeeded()
        advanceCurrentStringIfNeeded()

        var sliceSymbolsRemaining = size
        var stringPosition = currentStringPosition
        var viewedCurrentString = currentString
        var nextStringIndex = 0
        var slice = ""

        while (sliceSymbolsRemaining > 0) {
            if (viewedCurrentString.length == stringPosition) {

                if (upcomingStrings.size <= nextStringIndex) {
                    if (strings.hasNext()) {
                        val next = strings.next()
                        upcomingStrings.add(next)
                        symbolsRemaining += next.length
                    } else {
                        throw NoSuchElementException("Apparently hasNext and next didn't agree")
                    }
                }
                viewedCurrentString = upcomingStrings[nextStringIndex++]
                stringPosition = 0
            }

            val coercedEnd = (stringPosition + sliceSymbolsRemaining).coerceAtMost(viewedCurrentString.length)
            slice += viewedCurrentString.substring(stringPosition, coercedEnd)

            val segmentLength = coercedEnd - stringPosition
            sliceSymbolsRemaining -= segmentLength
            stringPosition += segmentLength
        }

        symbolsRemaining -= step
        currentStringPosition += step

        return slice
    }

    private fun advanceCurrentStringIfNeeded() {
        while (currentString.length <= currentStringPosition) {
            currentStringPosition = 0

            when {
                upcomingStrings.isNotEmpty() -> {
                    currentString = upcomingStrings.removeAt(0)
                    //upcomingStrings already had their symbols counted
                }
                strings.hasNext() -> {
                    currentString = strings.next()
                    symbolsRemaining += currentString.length
                }
                else -> {
                    return
                }
            }
        }
    }

    private fun grabMoreStringsIfNeeded() {
        while (symbolsRemaining < size && strings.hasNext()) {
            val next = strings.next()
            upcomingStrings.add(next)
            symbolsRemaining += next.length
        }
    }
}