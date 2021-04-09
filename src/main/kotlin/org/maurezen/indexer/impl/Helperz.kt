package org.maurezen.indexer.impl

import com.googlecode.javaewah32.EWAHCompressedBitmap32
import org.slf4j.Logger
import org.slf4j.LoggerFactory

//a set of filenames
typealias IndexEntry = HashSet<String>

//map file->map line->list offsets
typealias RichIndexEntry = HashMap<String, Map<Int, List<Int>>>

//@todo experiment on live data for 32-bit vs 64-bit decision
//  preliminary analysis suggests we prefer 32-bit due to smaller footprint
internal typealias IndexEntryInternal = EWAHCompressedBitmap32

fun IndexEntryInternal.intersectImmutable(another: IndexEntryInternal): IndexEntryInternal {
    return and(another)
}

fun IndexEntryInternal.addImmutable(what: IndexEntryInternal): IndexEntryInternal {
    return or(what)
}

infix fun <E: HashMap<K, IndexEntryInternal>, K> E.mergeMapBitMap(what: E): E {
    for ((key, value) in what) {
        this.merge(key, value, { oldValue, newValue -> oldValue.addImmutable(newValue) })
    }
    return this
}

/**
 * An equivalent of String.windowed(Int, Int) applicable to a sequence of strings instead, running without slurping the
 * whole thing in memory.
 *
 * Is absolutely NOT THREAD SAFE.
 */
fun Sequence<String>.windowedChars(size: Int, step: Int = 1): Sequence<String> {

    return Sequence { CrossSequenceWindowedCharIterator(size, step, iterator()) }
}

/**
 * Returns a sequence that inserts a separator between each two consecutive elements of this sequence
 *
 * The operation is _intermediate_
 */
fun <T> Sequence<T>.join(separator: T): Sequence<T> {
    val first = iterator()

    return Sequence { object: Iterator<T> {
        var pickFromFirst = true

        override fun hasNext() = first.hasNext()

        override fun next(): T {
            return if (pickFromFirst) {
                pickFromFirst = false
                first.next()
            } else {
                pickFromFirst = true
                separator
            }
        }
    } }
}

/**
 * Returns indices of the substring occurrences in a sequence of strings joined by a specified separator
 * (assumed to be an eol symbol)
 *
 * Indices are organized in a map line -> list offset
 *
 * This is basically a Knuth-Morris-Pratt implementation adjusted for multiline reality
 *
 * The operation is _terminal_
 */
fun Sequence<String>.indicesOf(substring: String, eol: String = defaultEOL): Map<Int, List<Int>> {

    val iterator = iterator()
    //map line -> list offset
    val result: HashMap<Int, MutableList<Int>> = linkedMapOf()

    if (!iterator.hasNext()) {
        return result
    }

    //a register of multiline tentative matches
    //map line -> pair -> offset for the start of the match, remaining length
    var tentative: Map<Int, Pair<Int, Int>> = linkedMapOf()

    var current: String
    var line = 0

    do {
        current = iterator.next()

        //take care of tentative registry first
        tentative = tentative
            //if current starts with the remaining, move from tentative to result at tentative line
            .filter { (line, pair) ->
                val remainingSubstring = substring.takeLast(pair.second)
                val wantToMove = current.startsWith(remainingSubstring)
                if (wantToMove) {
                    //it is by design initialized on a previous iteration
                    result[line]!!.add(pair.first)
                }
                wantToMove
            //otherwise check if maybe the remaining part of the substring startsWith current string
            }.mapValues { (_, pair) ->
                val remainingSubstring = substring.takeLast(pair.second)
                if (remainingSubstring.startsWith(current)) {
                    Pair(pair.first, pair.second - current.length)
                } else {
                    pair
                }
            }.toMutableMap()

        //take care of single-line matches
        result[line] = current.indicesOf(substring)

        val lastIndex = if (result[line]!!.isEmpty()) 0 else result[line]!!.last()

        //find overlap between current[lastIndex+1:] and substring
        val overlap = current.findOverlap(substring, lastIndex + 1)
        if (overlap != NONE) {
            //if there is _some_ overlap, we should register its length
            val overlapLength = current.length - overlap
            tentative[line] = Pair(overlap, substring.length - overlapLength)
        }
        //if there's no overlap, we know there will be no multiline matches starting on this line and can proceed
        if (current != eol) {
            line++
        }
    } while (iterator.hasNext())

    return result.filterValues(List<Int>::isNotEmpty)
}

/**
 * Finds if there's an overlap between this and other strings, starting no earlier than offset.
 *
 * Returns an index that is the start of the biggest possible overlap, or NONE in case there's no overlap
 */
const val NONE = -1
internal fun String.findOverlap(other: String, offset: Int = 0): Int {
    var overlap = NONE
    var tracker = 0
    for (i in offset until length) {
        if (this[i] == other[tracker]) {
            if (overlap == NONE) {
                overlap = i
            }
            tracker++
        } else {
            overlap = NONE
            tracker = 0
        }
    }
    return overlap
}

fun String.indicesOf(substring: String): MutableList<Int> {
    val result = mutableListOf<Int>()
    var start = 0
    while (run {start = indexOf(substring, start); start } > -1) {
        result.add(start)
        start++
    }
    return result
}


@Suppress("unused")
//grabbed from https://www.reddit.com/r/Kotlin/comments/8gbiul/slf4j_loggers_in_3_ways/
inline fun <reified T> T.logger(): Logger {
    if (T::class.isCompanion) {
        return LoggerFactory.getLogger(T::class.java.enclosingClass)
    }
    return LoggerFactory.getLogger(T::class.java)
}

const val alphabetBase = "abcdefghijklmnopqrstuvwxyz"
const val alphabetCyrillic = "абвгдеёжзийклмнопрстуфхцчшщъыьэюя"
const val numbers = "0123456789"
const val symbols = "~!@#$%^&*()_+-=,./?\\[]{}';\":|<>`"
const val extraSymbols = "©⋯–…’"
const val newlineLinux = "\n"
const val newLineWindows = "\r\n"
const val newLineMacOld = "\r"
const val whitespace = "\t "
val alphabet = alphabetBase + alphabetBase.toUpperCase() + alphabetCyrillic + alphabetCyrillic.toUpperCase()
val alphanumeric = alphabet + numbers
val alphabetExtendedSansNewline = alphanumeric + symbols + whitespace
val alphabetExtended = alphabetExtendedSansNewline + newlineLinux + newLineWindows + newLineMacOld + defaultEOL + extraSymbols