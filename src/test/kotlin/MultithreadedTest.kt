import kotlinx.coroutines.*
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.maurezen.indexer.Index
import org.maurezen.indexer.State
import org.maurezen.indexer.Stats
import org.maurezen.indexer.impl.*
import org.maurezen.indexer.impl.coroutines.IndexBuilderCoroutines
import org.maurezen.indexer.impl.multithreaded.IndexBuilderParallel
import org.maurezen.indexer.impl.naive.IndexBuilderNaive
import org.maurezen.indexer.impl.naive.buildStats
import java.io.File
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
import kotlin.system.measureTimeMillis

private const val fuzzyTestIterations = 20
private const val fuzzyQueries = 100000

private const val asyncTimeoutMs = 100
private const val cancellationTimeoutMS = 1000

private const val prefix = "MultithreadedTestFile"

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

        val naive = IndexBuilderNaive(n).with(filenames).buildFuture().get()
        val naiveParallel = IndexBuilderParallel(n).with(filenames).buildFuture().get()

        assert(compareResults(naive, "naive", naiveParallel, "parallel", seed)) {"Both index implementations are expected to yield the same results for seed $seed"}
    }

    @RepeatedTest(fuzzyTestIterations)
    fun naiveAndParallelYieldSameResultsCoroutines() = runBlocking {
        assert(n < maxQuerySize) {"n-gram indices don't support queries of less than n symbols"}

        val (filenames,seed) = generateRandomDatafiles(prefix)

        val naive = IndexBuilderNaive(n).with(filenames).buildFuture().get()
        val naiveParallel = IndexBuilderParallel(n).with(filenames).buildFuture().get()

        withContext(Dispatchers.Default) {
            assert(compareResultsParallel(naive, "naive", naiveParallel, "parallel", seed)) {"Both index implementations are expected to yield the same results for seed $seed"}
        }
    }

    @RepeatedTest(fuzzyTestIterations)
    fun naiveAndCoroutineYieldSameResults() = runBlocking {
        assert(n < maxQuerySize) {"n-gram indices don't support queries of less than n symbols"}

        val (filenames,seed) = generateRandomDatafiles(prefix)

        val naive = IndexBuilderNaive(n).with(filenames).buildFuture().get()
        val naiveCoroutines = IndexBuilderCoroutines(n).with(filenames).buildAsync().await()

        withContext(Dispatchers.Default) {
            assert(compareResults(naive, "naive", naiveCoroutines, "coroutine", seed)) {"Both index implementations are expected to yield the same results for seed $seed"}
        }
    }

    @RepeatedTest(fuzzyTestIterations)
    fun naiveAndCoroutineYieldSameResultsCoroutines() = runBlocking {
        assert(n < maxQuerySize) {"n-gram indices don't support queries of less than n symbols"}

        val (filenames,seed) = generateRandomDatafiles(prefix)

        val naive = IndexBuilderNaive(n).with(filenames).buildFuture().get()
        val naiveCoroutines = IndexBuilderCoroutines(n).with(filenames).buildAsync().await()

        withContext(Dispatchers.Default) {
            assert(compareResultsParallel(naive, "naive", naiveCoroutines, "coroutine", seed)) {"Both index implementations are expected to yield the same results for seed $seed"}
        }
    }

    @RepeatedTest(fuzzyTestIterations)
    fun naiveAndNaiveYieldSameResultsCoroutines() = runBlocking {
        assert(n < maxQuerySize) {"n-gram indices don't support queries of less than n symbols"}

        val (filenames,seed) = generateRandomDatafiles(prefix)
        val naive = IndexBuilderNaive(n).with(filenames).buildFuture().get()

        withContext(Dispatchers.Default) {
            assert(compareResultsParallel(naive, "naive", naive, "same naive", seed)) {"We expect a single instance to yield the same results for seed $seed"}
        }
    }

    @RepeatedTest(fuzzyTestIterations)
    fun naiveQueriesAreIdempotentInMultithreadedEnvironment() = runBlocking {
        assert(n < maxQuerySize) { "n-gram indices don't support queries of less than n symbols" }

        val (filenames, seed) = generateRandomDatafiles(prefix)
        val naive = IndexBuilderNaive(n).with(filenames).buildFuture().get()

        val queryResults: HashMap<String, Pair<UserIndexEntry, Stats>> =
            hashMapOf()
        val resultsQueue = ConcurrentLinkedQueue<Pair<String, Pair<UserIndexEntry, Stats>>>()

        //using runBlocking for barrier synchronization
        runBlocking(Dispatchers.Default) {
            repeat(fuzzyQueries) {
                launch {
                    val random = ThreadLocalRandom.current()!!
                    val query = generateRandomQuery(random.nextInt(n + 1, maxQuerySize + 1), random)
                    val queryResult = naive.query(query)
                    resultsQueue.add(Pair(query, Pair(queryResult, queryResult.buildStats())))
                }
            }
        }
        resultsQueue.forEach { queryResults[it.first] = it.second }
        runBlocking(Dispatchers.Default) {
            val percentageThreshold = 0.98
            assert(queryResults.keys.size > percentageThreshold * fuzzyQueries) { "We expect a high percentage of unique queries, got just ${queryResults.keys.size} out of $fuzzyQueries attempts which is lower than expected $percentageThreshold" }
            coroutinize(queryResults.keys) {
                val secondPass = naive.query(it)
                assert(queryResults[it]!!.first == secondPass) { "We expect both passes to yield similar results for seed $seed and query $it; got \n${queryResults[it]!!.first}\n for the first pass and \n$secondPass\n for the second pass instead" }
                assert(queryResults[it]!!.second == secondPass.buildStats()) { "We expect both passes to yield results with similar stats for seed $seed and query $it; got \n${queryResults[it]!!.second}\n and \n${secondPass.buildStats()}\n instead (results seem to be similar: \n$secondPass\n)" }

            }
        }
    }

    @Test
    fun coroutineAsyncIsAsync() {
        assert(n < maxQuerySize) {"n-gram indices don't support queries of less than n symbols"}

        val (filenames, _) = generateRandomDatafiles(prefix)

        val indexBuilderCoroutines = IndexBuilderCoroutines(n)
        assert ( indexBuilderCoroutines.status() == State.INITIAL) { "we expect index builder to be in initial state"}

        val startIndexing = System.currentTimeMillis()
        val naiveCoroutines = indexBuilderCoroutines.with(filenames).buildAsync()
        val timeIndexing = System.currentTimeMillis() - startIndexing

        assert(timeIndexing < asyncTimeoutMs) { "We expect async methods to be, well, async and return within $asyncTimeoutMs ms, not in $timeIndexing ms" }
        assert(naiveCoroutines.isActive) { "We expect async index computation not finish yet" }
        val status = indexBuilderCoroutines.status()
        assert(status == State.BUILD) { "We expect indexer to be in build state, not in $status" }

        indexBuilderCoroutines.cancelUpdate()
    }

    @Test
    fun coroutineIsCancelable() = runBlocking {
        assert(n < maxQuerySize) {"n-gram indices don't support queries of less than n symbols"}

        val (filenames, _) = generateRandomDatafiles(prefix)

        val indexBuilderCoroutines = IndexBuilderCoroutines(n)

        assert(indexBuilderCoroutines.status() == State.INITIAL) { "we expect index builder to be in initial state" }

        indexBuilderCoroutines.with(filenames).buildAsync()

        val time = measureTimeMillis {
            indexBuilderCoroutines.cancelUpdate()
        }

        assert(time < cancellationTimeoutMS) { "Time to cancel index building should be under $cancellationTimeoutMS ms, was $time instead" }
        assert(indexBuilderCoroutines.status() == State.INITIAL) { "we expect index builder to be in initial state after cancellation" }
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

            val firstLines = reader.readAsList(it.first.absolutePath)
            val secondLines = reader.readAsList(it.second.absolutePath)

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

    private fun reportDifference(first: UserIndexEntry, firstName: String, second: UserIndexEntry, secondName: String, query: String, seed: Int) {
        println("Result mismatch for seed $seed and query \"$query\": \n$firstName returned $first \n$secondName returned $second")
    }

    private fun reportDifference(first: RichUserIndexEntry, firstName: String, second: RichUserIndexEntry, secondName: String, query: String, seed: Int) {
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

    private val logger = logger()

    private suspend fun <T> coroutinize(what: Iterable<T>, action: suspend (T) -> Unit) {
        val time = measureTimeMillis {
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
}

