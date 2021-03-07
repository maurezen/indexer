package org.maurezen.indexer.impl

import com.googlecode.javaewah32.EWAHCompressedBitmap32
import org.slf4j.Logger
import org.slf4j.LoggerFactory

typealias UserIndexEntry = HashSet<String>

//@todo experiment on live data for 32-bit vs 64-bit decision
//  preliminary analysis suggests we prefer 32-bit due to smaller footprint
typealias IndexEntry = EWAHCompressedBitmap32

fun IndexEntry.intersectImmutable(another: IndexEntry): IndexEntry {
    return and(another)
}

fun IndexEntry.addImmutable(what: IndexEntry): IndexEntry {
    return or(what)
}

infix fun <E: HashMap<K, IndexEntry>, K> E.mergeMapBitMap(what: E): E {
    for ((key, value) in what) {
        this.merge(key, value, { oldValue, newValue -> oldValue.addImmutable(newValue) })
    }
    return this
}

internal fun Logger.warnIfEnabled(messageSupplier: () -> String) {
    if (isWarnEnabled) {
        warn(messageSupplier())
    }
}

internal fun Logger.debugIfEnabled(messageSupplier: () -> String) {
    if (isDebugEnabled) {
        debug(messageSupplier())
    }
}

//grabbed from https://www.reddit.com/r/Kotlin/comments/8gbiul/slf4j_loggers_in_3_ways/
inline fun <reified T> T.logger(): Logger {
    if (T::class.isCompanion) {
        return LoggerFactory.getLogger(T::class.java.enclosingClass)
    }
    return LoggerFactory.getLogger(T::class.java)
}

const val alphabetBase = "abcdefghijklmnopqrstuvwxyz"
const val numbers = "0123456789"
const val symbols = "~!@#$%^&*()_+-=,./?\\[]{}';\":|<>`"
const val whitespace = "\t "
val alphabet = alphabetBase + alphabetBase.toUpperCase()
val alphanumeric = alphabet + numbers
val alphabetExtendedSansNewline = alphanumeric + symbols + whitespace
val alphabetExtended = alphabetExtendedSansNewline + "\n"