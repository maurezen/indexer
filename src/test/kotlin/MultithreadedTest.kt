import arrow.core.Either
import kotlinx.coroutines.*
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.maurezen.indexer.*
import org.maurezen.indexer.impl.*
import org.maurezen.indexer.impl.coroutines.IndexBuilderCoroutines
import org.maurezen.indexer.impl.multithreaded.IndexBuilderParallel
import org.maurezen.indexer.impl.naive.IndexBuilderNaive
import org.maurezen.indexer.impl.naive.buildStats
import java.io.File
import java.io.FileFilter
import java.lang.String.format
import java.nio.file.Files
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.IntConsumer
import java.util.stream.IntStream
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.random.Random

private const val fuzzyTestIterations = 20
private const val fuzzyQueries = 100000

private const val prefix = "MultithreadedTestFile"

private const val IDEA_IS_HERE = "ORG_MAUREZEN_INDEXER_IDEA_FOLDER_PRESENT"
private const val IDEA_PATH = "ORG_MAUREZEN_INDEXER_IDEA_PATH"

class MultithreadedTest {

    private val n = 3

    private val maxFiles = 20
    private val maxFileSize = 102400

    private val maxLineSize = 64
    private val maxQuerySize = 10

    private val reader = FileReaderBasic()

    @RepeatedTest(fuzzyTestIterations)
    fun naiveAndParallelYieldSameResults() = runBlocking {
        assert(n < maxQuerySize) {"n-gram indices don't support queries of less than n symbols"}

        val (filenames,seed) = generateRandomDatafiles(prefix)

        val naive = IndexBuilderNaive(n).with(filenames).buildAsync().await()
        val naiveParallel = IndexBuilderParallel(n).with(filenames).buildAsync().await()

        assert(compareResults(naive, "naive", naiveParallel, "parallel", seed)) {"Both index implementations are expected to yield the same results for seed $seed"}
    }

    @RepeatedTest(fuzzyTestIterations)
    fun naiveAndParallelYieldSameResultsCoroutines() = runBlocking {
        assert(n < maxQuerySize) {"n-gram indices don't support queries of less than n symbols"}

        val (filenames,seed) = generateRandomDatafiles(prefix)

        val naive = IndexBuilderNaive(n).with(filenames).buildAsync().await()
        val naiveParallel = IndexBuilderParallel(n).with(filenames).buildAsync().await()

        withContext(Dispatchers.Default) {
            assert(compareResultsParallel(naive, "naive", naiveParallel, "parallel", seed)) {"Both index implementations are expected to yield the same results for seed $seed"}
        }
    }

    @RepeatedTest(fuzzyTestIterations)
    fun naiveAndCoroutineYieldSameResults() = runBlocking {
        assert(n < maxQuerySize) {"n-gram indices don't support queries of less than n symbols"}

        val (filenames,seed) = generateRandomDatafiles(prefix)

        val naive = IndexBuilderNaive(n).with(filenames).buildAsync().await()
        val naiveCoroutines = IndexBuilderCoroutines(n).with(filenames).buildAsync().await()

        withContext(Dispatchers.Default) {
            assert(compareResults(naive, "naive", naiveCoroutines, "coroutine", seed)) {"Both index implementations are expected to yield the same results for seed $seed"}
        }
    }

    @RepeatedTest(fuzzyTestIterations)
    fun naiveAndCoroutineYieldSameResultsCoroutines() = runBlocking {
        assert(n < maxQuerySize) {"n-gram indices don't support queries of less than n symbols"}

        val (filenames,seed) = generateRandomDatafiles(prefix)

        val naive = IndexBuilderNaive(n).with(filenames).buildAsync().await()
        val naiveCoroutines = IndexBuilderCoroutines(n).with(filenames).buildAsync().await()

        withContext(Dispatchers.Default) {
            assert(compareResultsParallel(naive, "naive", naiveCoroutines, "coroutine", seed)) {"Both index implementations are expected to yield the same results for seed $seed"}
        }
    }

    @RepeatedTest(fuzzyTestIterations)
    fun naiveAndNaiveYieldSameResultsCoroutines() = runBlocking {
        assert(n < maxQuerySize) {"n-gram indices don't support queries of less than n symbols"}

        val (filenames,seed) = generateRandomDatafiles(prefix)
        val naive = IndexBuilderNaive(n).with(filenames).buildAsync().await()

        withContext(Dispatchers.Default) {
            assert(compareResultsParallel(naive, "naive", naive, "same naive", seed)) {"We expect a single instance to yield the same results for seed $seed"}
        }
    }

    @RepeatedTest(fuzzyTestIterations)
    fun naiveQueriesAreIdempotentInMultithreadedEnvironment() = runBlocking {
        assert(n < maxQuerySize) { "n-gram indices don't support queries of less than n symbols" }

        val (filenames, seed) = generateRandomDatafiles(prefix)
        val index = IndexBuilderNaive(n).with(filenames).buildAsync().await()

        val firstPassResults = queryRandomly(index).associateBy({ it.first }, { it.second })

        val percentageThreshold = 0.98
        assert(firstPassResults.keys.size > percentageThreshold * fuzzyQueries) { "We expect a high percentage of unique queries, got just ${firstPassResults.keys.size} out of $fuzzyQueries attempts which is lower than expected $percentageThreshold" }

        queryDeterministically(index, firstPassResults.keys) { query, secondPass ->
            assert(firstPassResults[query]!!.first == secondPass) { "We expect both passes to yield similar results for seed $seed and query $query; got \n${firstPassResults[query]!!.first}\n for the first pass and \n$secondPass\n for the second pass instead" }
            assert(firstPassResults[query]!!.second == secondPass.buildStats()) { "We expect both passes to yield results with similar stats for seed $seed and query $query; got \n${firstPassResults[query]!!.second}\n and \n${secondPass.buildStats()}\n instead (results seem to be similar: \n$secondPass\n)" }
        }
    }

    @RepeatedTest(fuzzyTestIterations)
    fun windowedWorksCorrectlyOnSequences() {
        val (filenames, seed) = generateRandomDatafiles(prefix)

        readFilesAndCompareWindowed(filenames, seed, true)
    }

    @Test
    @EnabledIfEnvironmentVariable(named = IDEA_IS_HERE, matches = "true")
    fun windowedWorksCorrectlyOnIdeaSource() {
        val ideaPath = System.getenv().get(IDEA_PATH)!!

        val filenames = reader.explodeFileRoots(listOf(ideaPath))

        MultithreadedTest().readFilesAndCompareWindowed(filenames, 42, printFilenames = true)
    }

    private fun readFilesAndCompareWindowed(filenames: Iterable<String>, seed: Int, printContents: Boolean = false, printFilenames: Boolean = true) {
        val reader = FileReaderBasic()

        filenames.forEach { filename ->
            if (printFilenames) println(filename)
            readFileAndCompareWindowed(reader, filename, seed, printContents)
        }
    }

    private fun readFileAndCompareWindowed(
        reader: FileReaderBasic,
        filename: String,
        seed: Int,
        printContents: Boolean = false
    ) {
        val joinToString = mutableListOf<String>()
        val fileContentsSingleString = readLinesNotExpectingIssues(reader, filename).joinToString(defaultEOL)

        fileContentsSingleString.windowed(3).forEach { joinToString.add(it) }
        val windowedChars = mutableListOf<String>()

        reader.readAnd(filename) { strings ->
            strings.join(defaultEOL).windowedChars(3).forEach { windowedChars.add(it) }
        }

        assert (windowedChars == joinToString) {
            "Different results for different windowing methods\nSeed: $seed filename: $filename\n" + (if (printContents) "File contents:\n$fileContentsSingleString" else "")}
    }

    @Test
    fun userCanCancelDeferred() = runBlocking {
        assert(n < maxQuerySize) {"n-gram indices don't support queries of less than n symbols"}

        val (filenames, _) = generateRandomDatafiles(prefix)

        val indexBuilderCoroutines = IndexBuilderCoroutines(n).with(filenames)
        val coroutinesDeferred = indexBuilderCoroutines.buildAsync()

        assertIndexNotReadyYet(indexBuilderCoroutines)

        coroutinesDeferred.cancel("Test cancellation")

        assertThrows<CancellationException>(" Canceled deferred should throw on await ") { coroutinesDeferred.await() }
        assertIndexNotReadyYet(indexBuilderCoroutines)
    }


    private fun assertIndexNotReadyYet(indexBuilder: IndexBuilder) {
        assert (indexBuilder.get() == EmptyIndex) {"we expect index builder to be in initial state"}
    }

    @RepeatedTest(fuzzyTestIterations)
    fun generateRandomDataFilesIsDeterministicBasedOnSeed() {
        val prefix = "generateRandomDataFilesIsDeterministicBasedOnSeed"
        val (firstFilenames, seed) = generateRandomDatafiles(prefix)

        val secondFilenames = generateRandomDatafiles(prefix, seed)

        assert(compareFilesets(firstFilenames, secondFilenames)) {"Two datasets created on the same seed $seed should be equal"}
    }

    @RepeatedTest(fuzzyTestIterations)
    fun generateRandomDataFilesInADirIsDeterministicBasedOnSeed() {
        val prefix = "generateRandomDataFilesInADirIsDeterministicBasedOnSeed"
        val seed: Int = Random.nextInt()

        compareRandomDatafilesForSeed(prefix, seed)
    }

    /**
     * Tests the seeds that we had reproducible failures for
     */
    @ParameterizedTest
    @ValueSource(ints = [-685560897, -838403338])
    fun generateDataFilesInADirIsDeterministicBasedOnFailedSeeds(seed: Int) {
        compareRandomDatafilesForSeed("generateDataFilesInADirIsDeterministicBasedOnSeed", seed)
    }

    @Test
    fun indexerHandlesFileDisappearanceResilientlyOnQuery() = runBlocking {
        val prefix = "indexerHandlesFileDisappearanceResilientlyOnQuery"

        val (filenames, seed) = generateRandomDatafiles(prefix)
        val filenameToDisappear = filenames.random()

        val index = IndexBuilderCoroutines(n).with(filenames).buildAsync().await()

        val firstPassResults = queryRandomly(index).associateBy({ it.first }, { it.second })

        assert(File(filenameToDisappear).delete()) { "We expect we're able to delete one of our temporary test files $filenameToDisappear" }

        queryDeterministically(index, firstPassResults.keys) { query, secondPass ->
            val firstPass = firstPassResults[query]!!.first
            firstPass.remove(filenameToDisappear)

            assert(secondPass[filenameToDisappear]?.isEmpty() ?: true) { "We expect the results of the second pass to not contain anything regarding removed file $filenameToDisappear" }

            secondPass.remove(filenameToDisappear)
            assert(firstPass == secondPass) { "We expect the results to be identical except the part corresponding to the missing file $filenameToDisappear for seed $seed and query $query; got \n${firstPassResults[query]!!.first}\n for the first pass and \n$secondPass\n for the second pass instead" }
        }
    }

    private fun queryDeterministically(index: Index, queries: Iterable<String>, consumer: (String, RichIndexEntry) -> Unit) = runBlocking(Dispatchers.Default) {
        coroutinize(queries) { consumer.invoke(it, index.queryAndScan(it)) }
    }

    private fun queryRandomly(index: Index) = runBlocking(Dispatchers.Default) {
        val resultsQueue = ConcurrentLinkedQueue<Pair<String, Pair<RichIndexEntry, Stats>>>()
        repeat(fuzzyQueries) {
            launch {
                val random = ThreadLocalRandom.current()!!
                val query = generateRandomQuery(random.nextInt(n + 1, maxQuerySize + 1), random)
                val queryResult = index.queryAndScan(query)
                resultsQueue.add(Pair(query, Pair(queryResult, queryResult.buildStats())))
            }
        }
        resultsQueue
    }

    @Test
    fun indexerHandlesFileDisappearanceResilientlyWhileIndexing() = runBlocking {
        val prefix = "indexerHandlesFileDisappearanceResilientlyWhileIndexing"

        val (filenames, seed) = generateRandomDatafiles(prefix)
        val filenameToDisappear = filenames.random()

        val deletingFileReader = object : FileReader {
            val actualReader = FileReaderBasic()

            override fun <T> readAnd(filename: String, block: (Sequence<String>) -> T): Either<Unit, T> {
                return actualReader.readAnd(filename, block)
            }

            override fun explodeFileRoots(roots: List<String>, filter: FileFilter): ArrayList<String> {
                val fileList = actualReader.explodeFileRoots(roots, filter)
                assert(File(filenameToDisappear).delete()) { "We expect we're able to delete one of our temporary test files $filenameToDisappear" }
                return fileList
            }
        }

        val index = IndexBuilderCoroutines(n).with(filenames).readBy(deletingFileReader).buildAsync().await()

        val firstPassResults = queryRandomly(index).associateBy({ it.first }, { it.second })

        queryDeterministically(index, firstPassResults.keys) { query, secondPass ->
            val firstPass = firstPassResults[query]!!.first
            firstPass.remove(filenameToDisappear)

            assert(secondPass[filenameToDisappear]?.isEmpty() ?: true) { "We expect the results of the second pass to not contain anything regarding removed file $filenameToDisappear" }

            secondPass.remove(filenameToDisappear)
            assert(firstPass == secondPass) { "We expect the results to be identical except the part corresponding to the missing file $filenameToDisappear for seed $seed and query $query; got \n${firstPassResults[query]!!.first}\n for the first pass and \n$secondPass\n for the second pass instead" }
        }
    }

    @RepeatedTest(fuzzyTestIterations)
    fun generatesIdenticalStringsForIdenticalSeeds() {
        val seed = Random.nextInt()
        val firstRandom = Random(seed)
        val secondRandom = Random(seed)

        val firstLength = generateLineLength(firstRandom)
        val secondLength = generateLineLength(secondRandom)
        assert(firstLength == secondLength) { "For seed {$seed} identical lengths should have been generated. Got {$firstLength} and {$secondLength} instead" }

        val firstString = generateRandomString(firstRandom, firstLength)
        val secondString = generateRandomString(secondRandom, secondLength)
        assert(firstLength == secondLength) { "For seed {$seed} identical strings should have been generated. Got QUOTE\n{$firstString}\nUNQUOTE and QUOTE{$secondString}\nUNQUOTE instead" }
    }

    private fun compareRandomDatafilesForSeed(prefix: String, seed: Int) {
        val firstDir = generateRandomDatafilesInADir(prefix, seed)
        val secondDir = generateRandomDatafilesInADir(prefix, seed)

        compareFilesets(firstDir, secondDir, seed)
    }

    private fun compareFilesets(firstFilenames: List<String>, secondFilenames: List<String>): Boolean {
        if (firstFilenames.size != secondFilenames.size) {
            return false
        } else {
            firstFilenames.zip(secondFilenames).forEach {
                val firstLines = reader.readAsList(it.first)
                val secondLines = reader.readAsList(it.second)

                if (firstLines != secondLines) {
                    return false
                }
            }
        }
        return true
    }

    private fun compareFilesets(firstDir: String, secondDir: String, seed: Int) {
        val first = File(firstDir)
        val second = File(secondDir)

        assert (first.isDirectory) { "For seed $seed we expect $firstDir to be a directory"}
        assert (second.isDirectory) { "For seed $seed we expect $secondDir to be a directory"}

        val firstFiles = first.listFiles()!!
        val secondFiles = second.listFiles()!!

        assert(firstFiles.size == secondFiles.size) {"For seed $seed we expect to have the same amount of files, not ${firstFiles.size} and ${secondFiles.size}"}

        firstFiles.sortBy(File::getName)
        secondFiles.sortBy(File::getName)

        firstFiles.zip(secondFiles).forEach {
            assert( it.first.length() == it.second.length()) { "For seed $seed we expect \n${it.first.absolutePath}\n and \n${it.second.absolutePath}\n have equal size, got ${it.first.length()} and ${it.second.length()} instead. \n Overall, we have file sizes (in bytes) as follows: \n${firstFiles.map(File::length)}\n for the first set and \n${secondFiles.map(File::length)}\n for the second set" }

            val firstLines = readLinesNotExpectingIssues(reader, it.first.absolutePath)
            val secondLines = readLinesNotExpectingIssues(reader, it.second.absolutePath)

            assert(firstLines.size == secondLines.size) { "For seed $seed we expect \n${it.first.absolutePath}\n and \n${it.second.absolutePath}\n have equal amount of lines, got ${firstLines.size} and ${secondLines.size} instead. \n Overall, we have file sizes (in lines) as follows: \n${firstFiles.map(File::lines)}\n for the first set and \n${secondFiles.map(File::lines)}\n for the second set" }
            assert(firstLines == secondLines) {"For seed $seed we expect \n${it.first.absolutePath}\n and \n${it.second.absolutePath}\n have equal contents, got \n${firstLines.joinToString("\n")}\nand\n${secondLines.joinToString("\n")}\ninstead"}
        }
    }

    private suspend fun compareResultsParallel(first: Index, firstName: String, second: Index, secondName: String, seed: Int, failFast : Boolean = true): Boolean {

        val resultsAreIdentical = AtomicBoolean(true)

        coroutineScope {
            repeat(fuzzyQueries) {
                launch {
                    if (resultsAreIdentical.get() || (!resultsAreIdentical.get() && !failFast)) {
                        val random = ThreadLocalRandom.current()!!
                        val query = generateRandomQuery(random.nextInt(n + 1, maxQuerySize + 1), random)

                        if (!areResultsIdenticalForQuery(query, first, second, firstName, secondName, seed)) {
                            println("Results mismatch for query \"$query\"")
                            if (failFast) {
                                println("Fail fast requested, skipping the rest of queries for this input")
                            }
                            resultsAreIdentical.set(false)
                        }
                    }
                }
            }
        }

        return resultsAreIdentical.get()
    }

    private fun compareResults(first: Index, firstName: String, second: Index, secondName: String, seed: Int, random: Random = Random(Random.nextInt()), failFast : Boolean = true): Boolean {
        val atomic = AtomicBoolean(true)

        val spliterator = IntStream.rangeClosed(1, fuzzyQueries).spliterator()
        spliterator.forEachRemaining (IntConsumer {
            if (atomic.get()) {
                lateinit var query: String
                synchronized(random) {
                    query = generateRandomQuery(random.nextInt(n + 1, maxQuerySize + 1), random)
                }

                if (!areResultsIdenticalForQuery(query, first, second, firstName, secondName, seed)) {
                    if (failFast) {
                        println("Results mismatch for query \"$query\"")
                        println("Fail fast requested, skipping the rest of queries for this input")
                    }
                    atomic.set(false)
                }
            }
        })

        return atomic.get()
    }

    private fun areResultsIdenticalForQuery(query: String, first: Index, second: Index, firstName: String, secondName: String, seed: Int): Boolean {
        val resultFirst = first.queryAndScan(query)
        val resultSecond = second.queryAndScan(query)

        if (resultFirst != resultSecond) {
            reportDifference(resultFirst, firstName, resultSecond, secondName, query, seed)
            return false
        }
        return true
    }

    private fun generateRandomDatafiles(prefix: String): Pair<List<String>, Int> {
        val seed: Int = Random.nextInt()

        return Pair(generateRandomDatafiles(prefix, seed), seed)
    }

    private fun generateRandomDatafilesInADir(prefix: String): Pair<String, Int> {
        val seed: Int = Random.nextInt()

        return Pair(generateRandomDatafilesInADir(prefix, seed), seed)
    }

    private fun reportDifference(first: IndexEntry, firstName: String, second: IndexEntry, secondName: String, query: String, seed: Int) {
        println("Result mismatch for seed $seed and query \"$query\": \n$firstName returned $first \n$secondName returned $second")
    }

    private fun reportDifference(first: RichIndexEntry, firstName: String, second: RichIndexEntry, secondName: String, query: String, seed: Int) {
        println("Result mismatch for seed $seed and query \"$query\": \n$firstName returned $first \n$secondName returned $second")
    }

    private fun generateRandomDatafiles(prefix: String, seed: Int): List<String> {
        val random = Random(seed)

        val filesQty = random.nextInt(maxFiles / 2, maxFiles + 1)
        val filenames: ArrayList<String> = arrayListOf()

        val numerator = tempFileNumeratorTemplate(filesQty)
        repeat (filesQty) {
            val fileSize = random.nextInt(maxFileSize / 1024, maxFileSize + 1)

            val file = File.createTempFile(prefix+format(numerator, it), "")
            filenames.add(file.absolutePath)
            file.setWritable(true)
            file.deleteOnExit()
            generateFileContents(file, fileSize, random)
        }

        return filenames
    }

    private fun generateRandomDatafilesInADir(prefix: String, seed: Int): String {
        val random = Random(seed)

        val filesQty = random.nextInt(maxFiles / 2, maxFiles + 1)
        val filenames: ArrayList<String> = arrayListOf()

        val tempDir = Files.createTempDirectory(prefix).toFile()
        tempDir.deleteOnExit()

        val numerator = tempFileNumeratorTemplate(filesQty)
        repeat (filesQty) {
            val fileSize = random.nextInt(maxFileSize / 1024, maxFileSize + 1)

            val file = Files.createTempFile(tempDir.toPath(), prefix+format(numerator, it), "").toFile()
            filenames.add(file.absolutePath)
            file.setWritable(true)
            file.deleteOnExit()
            generateFileContents(file, fileSize, random)
        }

        return tempDir.absolutePath
    }

    private fun tempFileNumeratorTemplate(filesQty: Int) = "%0${ceil(log10(filesQty * 1.0)).toInt()}d_"

    private fun generateFileContents(file: File, fileSize: Int, random: Random) {
        file.bufferedWriter().use {
            var elapsed = 0
            val builder = StringBuilder("")

            while (elapsed < fileSize) {
                val length = generateLineLength(random)
                elapsed += length
                builder.append("\n")
                builder.append(generateRandomString(random, length))
            }

            it.write(builder.toString())
            it.flush()
        }
    }

    private fun generateLineLength(random: Random) = random.nextInt(maxLineSize / 10, maxLineSize + 1)

    private fun generateRandomQuery(length: Int, random: Random): String {
        return generateRandomString(random, length, true)
    }

    private fun generateRandomQuery(length: Int, random: java.util.Random): String {
        return generateRandomString(random, length, true)
    }

    private fun generateRandomString(random: Random, length: Int, newlines: Boolean = false): String {
        val source = (if (newlines) alphabetExtended else alphabetExtendedSansNewline).toCharArray()

        return (1..length).map { source[random.nextInt(source.size)] }.joinToString(separator = "")
    }

    private fun generateRandomString(random: java.util.Random, length: Int, newlines: Boolean = false): String {
        val source = (if (newlines) alphabetExtended else alphabetExtendedSansNewline).toCharArray()

        return (1..length).map { source[random.nextInt(source.size)] }.joinToString(separator = "")
    }

    private suspend fun <T> coroutinize(what: Iterable<T>, action: suspend (T) -> Unit) {
        coroutineScope {
            // we're perfectly fine with a coroutine per item @ up to 1m elements and possibly more
            what.forEachIndexed { _: Int, it: T ->
                launch {
                    action(it)
                }
            }
        }
    }
}

