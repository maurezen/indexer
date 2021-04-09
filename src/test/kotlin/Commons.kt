import org.maurezen.indexer.impl.FileReaderBasic
import java.io.File

class Locator {}

val binaryFilename: String = Locator::class.java.getResource("/Command_Line_App.zip").path
val filename: String = Locator::class.java.getResource("/foobar").path
val filenames = listOf(Locator::class.java.getResource("/foobar").path, Locator::class.java.getResource("/baz").path)

fun printStrings(list: Sequence<String>) {
    for ((i, s) in list.withIndex()) {
        println("%3d:%s".format(i, s))
    }
}

fun printStrings(list: List<String>) {
    for ((i, s) in list.withIndex()) {
        println("%3d:%s".format(i, s))
    }
}

val reader = FileReaderBasic()
fun <T> readTestFile(block: (Sequence<String>) -> T) = reader.readAnd(filename, block)
fun <T> readTestFiles(block: (Sequence<String>) -> T) = reader.readAnd(filenames, block)
fun readTestBinaryFile() = reader.readAsList(binaryFilename)

fun File.lines(): Int {
    return reader.readAsList(absolutePath).size
}