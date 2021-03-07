import org.maurezen.indexer.impl.read

class Locator {}

val binaryFilename: String = Locator::class.java.getResource("/Command_Line_App.zip").path
val filename: String = Locator::class.java.getResource("/foobar").path
val filenames = listOf(Locator::class.java.getResource("/foobar").path, Locator::class.java.getResource("/baz").path)

fun printStrings(list: List<String>) {
    for ((i, s) in list.withIndex()) {
        println("%3d:%s".format(i, s))
    }
}

fun readTestFile() = read(filename)
fun readTestFiles() = read(filenames)
fun readTestBinaryFile() = read(binaryFilename)