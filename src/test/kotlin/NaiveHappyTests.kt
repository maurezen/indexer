import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.maurezen.indexer.Index
import org.maurezen.indexer.impl.*
import org.maurezen.indexer.impl.NGram.Companion.ngram
import org.maurezen.indexer.impl.NGram.Companion.ngramReverse
import org.maurezen.indexer.impl.multithreaded.IndexBuilderParallel
import org.maurezen.indexer.impl.naive.IndexBuilderNaive
import java.io.File
import java.util.concurrent.Future

class NaiveHappyTests {

    private val n = 3

    @Test
    fun readsAndPrintsTestFile() {
        val list = readTestFile()

        printStrings(list)
    }

    @Test
    fun readsAndPrintsTestBinaryFile() {
        val list = readTestBinaryFile()

        printStrings(list)
    }

    @Test
    fun readsTestFileAndSplitsFirstStringToNgrams() {
        val list = readTestFile()

        println(list[0])
        println(ngram(list[0], n).joinToString())
    }

    @Test
    fun readsTestFileAndSplitsItToNgrams() {
        val list = readTestFile()

        printStrings(list)
        println(ngram(list, n).joinToString(","))
    }

    @Test
    fun readsTestFileAndSplitsItToNgramsWithReverseIndex() {
        val list = readTestFile()

        printStrings(list)
        println(ngramReverse(list, n))
    }

    private fun readAndIndexTestFile(): Index = runBlocking {
        IndexBuilderNaive(3).with(filename).buildFuture().get()
    }

    private fun readAndIndexTestFiles(): Index = runBlocking {
        IndexBuilderNaive(n).with(readTestFiles().keys).buildFuture().get()
    }

    private fun readAndIndexTestFilesMultithreaded(): Future<Index> = runBlocking {
        IndexBuilderParallel(n).with(readTestFiles().keys).buildFuture()
    }

    @Test
    fun readsTestFileAndBuildsIndexAndLookups() {
        readsAndPrintsTestFile()
        val index = readAndIndexTestFile()

        println(index.stats())

        val would = "Would"
        compareResultToExpectation(
            index.query(would),
            "{${File(filename).absolutePath}}",
            would
        )

        val _ha = " ha"
        compareResultToExpectation(
            index.query(_ha),
            "{${File(filename).absolutePath}}",
            _ha
        )

        val tincidunt = "tincidunt"
        compareResultToExpectation(
            index.query(tincidunt),
            "{${File(filename).absolutePath}}",
            tincidunt
        )

        val notHappening = "notHappening"
        compareResultToExpectation(
            index.query(notHappening),
            "{}",
            notHappening
        )
    }

    private fun compareResultToExpectation(entry: UserIndexEntry, expected: String, pattern: String) {
        assert(entry.post() == expected) { "We expect '$pattern' query return \n$expected\n, got \n${entry.post()}\n instead" }
    }

    @Test
    fun readsTwoFilesAndBuildsIndicesAndLookups() {
        val index = readAndIndexTestFiles()

        println(index.stats())

        val would = "Would"
        compareResultToExpectation(
            index.query(would),
            "{${File(filename).absolutePath}}",
            would
        )

        val _ha = " ha"
        compareResultToExpectation(
            index.query(_ha),
            "{${File(filename).absolutePath}}",
            _ha
        )

        val tincidunt = "tincidunt"
        compareResultToExpectation(
            index.query(tincidunt),
            "{${File(filenames[1]).absolutePath}, ${File(filenames[0]).absolutePath}}",
            tincidunt
        )
    }

    @Test
    fun readsTwoFilesAndBuildsIndicesMultithreadedAndLookups() {
        val index = readAndIndexTestFilesMultithreaded().get()

        println(index.stats())

        val would = "Would"
        compareResultToExpectation(
            index.query(would),
            "{${File(filename).absolutePath}}",
            would
        )

        val _ha = " ha"
        compareResultToExpectation(
            index.query(_ha),
            "{${File(filename).absolutePath}}",
            _ha
        )

        val tincidunt = "tincidunt"
        compareResultToExpectation(
            index.query(tincidunt),
            "{${File(filenames[1]).absolutePath}, ${File(filenames[0]).absolutePath}}",
            tincidunt
        )
    }

    @Test
    fun indexQueryIsIdempotent() {
        readsAndPrintsTestFile()
        val index = readAndIndexTestFile()

        println(index.stats())

        val would = "Would"
        compareResultToExpectation(
            index.query(would),
            index.query(would).post(),
            would
        )

        val _ha = " ha"
        compareResultToExpectation(
            index.query(_ha),
            index.query(_ha).post(),
            _ha
        )

        val tincidunt = "tincidunt"
        compareResultToExpectation(
            index.query(tincidunt),
            index.query(tincidunt).post(),
            tincidunt
        )
    }

    private fun UserIndexEntry.post(): String {
        val i = this.sorted().iterator()
        if (!i.hasNext()) return "{}"
        val sb = StringBuilder()
        sb.append('{')
        while (true) {
            val value = i.next()
            sb.append(value)
            if (!i.hasNext()) return sb.append('}').toString()
            sb.append(',').append(' ')
        }
    }

}