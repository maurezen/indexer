### What
A basic (text) file indexer library in Kotlin. Given a character sequence, an indexer finds all the occurrences of this sequence as a substring in a given set of files. Primary use case is indexing a moderately-sized codebase for subsequent searches.
 
### How to use

Point an indexer towards the directory you need indexed 
```kotlin
val index = IndexBuilderCoroutines()
    .with(dirName)
    .buildAsync().await()
```

shoot your queries at it after it's done

```kotlin
// this gets you just a set of filenames that contain this query string
// on the plus side, it doesn't have to read the files for that
val entry = index.query("foobar")
```

to get more details
```kotlin
// this gets you lines and line positions for each file
// on the flip side, index has to re-read the files for that
val richEntry = index.queryAndScan("lorem ipsum")
```

to update the index
```kotlin
val indexBuilder = IndexBuilderCoroutines()
    .with(dirName)

var index = indexBuilder.buildAsync.await()

// something something something

runBlocking {
    //update is a suspend fun. Control flow is completely up to you.
    index = indexBuilder.update()
}
```

### Advanced usage

Want more filesystem roots? Sure. As many as you would reasonably want. 
```kotlin
val indexBuilder = IndexBuilderCoroutines()
    .with(dirNameA)
    .with(dirNameB)
    .with(listOf(dirNameC, dirNameD, dirNameE))
```

Want only specific files? Apply file filter.
A default behaviour is to accept every file.
Directories are always accepted.
```kotlin
val filter = object:java.io.FileFilter { /* whatever */ }
val indexBuilder = IndexBuilderCoroutines()
    .with(dirName)
    .filter(filter)
```

Want only large files? Or only small files? Or want to run a complex heuristic on file contents? There's an extension point for that. See javadoc for the details.
A default behaviour is to accept everything; there's a sample whitelist-based implementation that discards files as soon as it encounters too many non-whitelisted characters. 
```kotlin
val inspector = object: org.maurezen.indexer.ContentInspector { /* whatever */ }
val indexBuilder = IndexBuilderCoroutines()
    .with(dirName)
    .inspectedBy(inspector)
```

Want to deal with non-standard file formats or encodings? Implement your own reader. See javadoc for the details.
A default behaviour is to assume files are UTF-8 encoded.
```kotlin
val reader = object: org.maurezen.indexer.FileReader { /* whatever */ }
val indexBuilder = IndexBuilderCoroutines()
    .with(dirName)
    .readBy(reader)
```

Want to share index between threads? Share a builder instance and request an index.
```kotlin
//thread A
val indexBuilder = IndexBuilderCoroutines()
    .with(dirName)
//thread B
val index = indexBuilder.get()
```

Have a more prolonged lifecycle? Want an update? Keep a builder instance to yourself and trigger a build again when needed.
`indexBuilder.get()` will be returning the previous index version until the new computation completes.
```kotlin
val indexBuilder = IndexBuilderCoroutines()
    .with(dirName) 

var index = indexBuilder.buildAsync().await()

//things happen here
//...
//and now it's time for a refresh

index = indexBuilder.buildAsync().await()
```

Changed your mind and don't want that refresh anymore? 
```kotlin
val indexBuilder = IndexBuilderCoroutines()
    .with(dirName) 

var indexDeferred = indexBuilder.buildAsync()

indexDeferred.cancel()
```

### Performance

While a robust performance setup doesn't exist as of now, here is the anecdotal data for indexing all the files of an `intellij-community-master` snapshot dated late 2020 on mostly-available (sub-10% idle usage) 5950x:
```
Size: 563 MB (590,787,068 bytes)
Contains: 120,686 Files, 26,343 Folders
Created: Saturday, December 12, 2020
```
```kotlin
IndexBuilderCoroutines()
    .with(INTELLIJ_COMMUNITY_MASTER)
    .inspectedBy(WhitelistCharacterInspector(5))
    .filter(ACCEPTS_EVERYTHING)
    .buildAsync().await()
```
```
.../dev/intellij-community-master/ indexed in 26704.4382 ms
```

While, again, a robust memory footprint measurement doesn't exist, a heap dump of a vm after indexing fits in ~260Mb. The indexing process itself, though, requires ~12G.

