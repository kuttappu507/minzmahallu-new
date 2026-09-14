plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}

// CI helper: surface Kotlin compiler errors as GitHub annotations.
if (System.getenv("GITHUB_ACTIONS") == "true") {
    val matcher = layout.buildDirectory.file("kotlin-matcher.json").get().asFile
    matcher.parentFile.mkdirs()
    matcher.writeText(
        """{"problemMatcher":[{"owner":"kotlin-compiler","pattern":[{"regexp":"^e: file://(.+?):(\\d+):(\\d+) (.+)$","file":1,"line":2,"column":3,"message":4}]}]}"""
    )
    println("::add-matcher::${matcher.absolutePath}")
}
