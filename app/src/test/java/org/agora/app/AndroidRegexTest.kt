package org.agora.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The tests run on the JVM, the app on Android, whose regular expressions are ICU's: a few things the JVM takes are
 * refused there with an exception. A refused pattern in a top-level value takes its whole file down the first time the
 * file is used - in 1.4.0-beta3 Java's `(?U)` in the AI's thinking line closed the app with the first message, while
 * every test passed. So the app's patterns keep to what both understand.
 */
class AndroidRegexTest {
    @Test
    fun `the app's regular expressions use nothing Android refuses`() {
        val sources = File("src/main/java")
        assertTrue("the app's sources (the tests run in app/)", sources.isDirectory)
        val refused = mutableListOf<String>()
        sources.walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            file.readLines().forEachIndexed { index, line ->
                // Comments may name them
                if (line.trimStart().let { it.startsWith("*") || it.startsWith("/*") || it.startsWith("//") }) return@forEachIndexed
                val found = INLINE_FLAGS.findAll(line).filter { match -> match.groupValues[1].any { it !in ICU_FLAGS } }.map { it.value } +
                    REFUSED_OPTIONS.filter { it in line }
                found.forEach { refused += "${file.relativeTo(sources)}:${index + 1}: $it" }
            }
        }
        assertTrue("Android refuses these (the JVM takes them):\n" + refused.joinToString("\n"), refused.isEmpty())
    }

    private companion object {
        /** The flags written into a pattern: `(?U)`, `(?i:` ... */
        val INLINE_FLAGS = Regex("""\(\?([a-zA-Z-]+)[):]""")
        /** The flags ICU knows. Not Java's U (Unicode classes): on Android \s, \w and \d are Unicode classes anyway. */
        const val ICU_FLAGS = "idmsuwx-"
        /** Options Android throws for when a pattern is made with them. */
        val REFUSED_OPTIONS = listOf("UNICODE_CHARACTER_CLASS", "CANON_EQ")
    }
}
