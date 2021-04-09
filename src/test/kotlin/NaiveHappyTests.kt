import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.maurezen.indexer.Index
import org.maurezen.indexer.impl.IndexEntry
import org.maurezen.indexer.impl.NGram.Companion.ngram
import org.maurezen.indexer.impl.NGram.Companion.ngramReverse
import org.maurezen.indexer.impl.RichIndexEntry
import org.maurezen.indexer.impl.coroutines.IndexBuilderCoroutines
import org.maurezen.indexer.impl.inspection.YesMan
import org.maurezen.indexer.impl.multithreaded.IndexBuilderParallel
import org.maurezen.indexer.impl.naive.IndexBuilderNaive
import java.io.File

class NaiveHappyTests {

    private val n = 3

    @Test
    fun readsAndPrintsTestFile() {
        readTestFile { printStrings(it) }
    }

    @Test
    fun readsAndPrintsTestBinaryFile() {
        val list = readTestBinaryFile()

        printStrings(list)
    }

    @Test
    fun readsTestFileAndSplitsFirstStringToNgrams() {
        readTestFile {
            val line = it.iterator().next()
            println(line)
            println(ngram(line, n).joinToString())
        }
    }

    @Test
    fun readsTestFileAndSplitsItToNgrams() {
        readTestFile { printStrings(it) }
        readTestFile { println(ngram(it, n).joinToString(",")) }
    }

    @Test
    fun readsTestFileAndSplitsItToNgramsWithReverseIndex() {
        readTestFile { printStrings(it) }
        readTestFile { println(ngramReverse(it, n, YesMan, "")) }
    }

    private fun readAndIndexTestFile(): Index = runBlocking {
        IndexBuilderNaive(3).with(filename).buildAsync().await()
    }

    private fun readAndIndexTestFiles(): Index = runBlocking {
        IndexBuilderNaive(n).with(readTestFiles{it.toList()}.keys).buildAsync().await()
    }

    private fun readAndIndexTestFilesMultithreaded(): Index = runBlocking {
        IndexBuilderParallel(n).with(readTestFiles{it.toList()}.keys).buildAsync().await()
    }

    private fun readAndIndexTestFilesCoroutines(): Index = runBlocking {
        IndexBuilderCoroutines(n).with(readTestFiles{it.toList()}.keys).buildAsync().await()
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

    private fun compareResultToExpectation(entry: RichIndexEntry, expected: String, pattern: String) {
        assert(entry.postSorted() == expected) { "We expect '$pattern' query return \n$expected\n, got \n${entry.postSorted()}\n instead" }
    }

    private fun compareResultToExpectation(entry: IndexEntry, expected: String, pattern: String) {
        assert(entry.postSorted() == expected) { "We expect '$pattern' query return \n$expected\n, got \n${entry.postSorted()}\n instead" }
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
        val index = readAndIndexTestFilesMultithreaded()

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
    fun readsTwoFilesAndBuildsIndicesCoroutinesAndScansAndLookups() = runBlocking {
        val index = readAndIndexTestFilesCoroutines()

        println(index.stats())

        val would = "Would"
        compareResultToExpectation(
            index.queryAndScan(would),
            "{${File(filename).absolutePath}={1=[0]}}",
            would
        )

        val _ha = " ha"
        compareResultToExpectation(
            index.queryAndScan(_ha),
            "{${File(filename).absolutePath}={1=[32], 3=[2], 4=[13]}}",
            _ha
        )

        val tincidunt = "tincidunt"
        compareResultToExpectation(
            index.queryAndScan(tincidunt),
            "{${File(filenames[1]).absolutePath}={7=[0]}, ${File(filenames[0]).absolutePath}={15=[0], 16=[0, 40]}}",
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
            index.query(would).postSorted(),
            would
        )

        val _ha = " ha"
        compareResultToExpectation(
            index.query(_ha),
            index.query(_ha).postSorted(),
            _ha
        )

        val tincidunt = "tincidunt"
        compareResultToExpectation(
            index.query(tincidunt),
            index.query(tincidunt).postSorted(),
            tincidunt
        )
    }

    private fun IndexEntry.postSorted(): String {
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

    private fun RichIndexEntry.postSorted(): String {
        val i = this.entries.sortedBy { entry -> entry.key }.iterator()
        if (!i.hasNext()) return "{}"
        val sb = StringBuilder()
        sb.append('{')
        while (true) {
            val e = i.next()
            val key = e.key
            val value = e.value
            sb.append(key)
            sb.append('=')
            sb.append(if (value === this) "(this Map)" else value)
            if (!i.hasNext()) return sb.append('}').toString()
            sb.append(',').append(' ')
        }
    }
}