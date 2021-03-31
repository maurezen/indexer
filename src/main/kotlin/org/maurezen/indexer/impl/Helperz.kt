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

fun String.indicesOf(substring: String): List<Int> {
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
const val newline = "\n"
const val whitespace = "\t "
val alphabet = alphabetBase + alphabetBase.toUpperCase() + alphabetCyrillic + alphabetCyrillic.toUpperCase()
val alphanumeric = alphabet + numbers
val alphabetExtendedSansNewline = alphanumeric + symbols + whitespace
val alphabetExtended = alphabetExtendedSansNewline + newline + extraSymbols