package gtc.designtokens

import java.io.Serializable

/** One raw design value found in a screen file: [path], 1-based [line] and the offending [match]. */
data class Violation(val path: String, val line: Int, val match: String) : Serializable

/**
 * Finds raw design values (dp/sp literals, colour literals, font construction, direct
 * `MaterialTheme` reads) that screen code must take from the design system instead.
 */
object RawValueScanner {
    private val dimension = Regex("""(?<![\w.])(\d+(?:\.\d+)?)\.(dp|sp)\b""")
    private val patterns = listOf(
        Regex("""\bColor\(\s*0x[0-9A-Fa-f_]+"""),
        Regex("""\bFontFamily\("""),
        Regex("""\bFont\("""),
        Regex("""\bMaterialTheme\.(?:colorScheme|typography|shapes)\b"""),
    )

    fun scan(relativePath: String, text: String): List<Violation> {
        val violations = mutableListOf<Violation>()
        blankLiteralsAndComments(text).lines().forEachIndexed { index, line ->
            val found = mutableListOf<MatchResult>()
            dimension.findAll(line).filterTo(found) { !(it.groupValues[2] == "dp" && isZero(it.groupValues[1])) }
            patterns.forEach { found += it.findAll(line) }
            found.sortedBy { it.range.first }
                .mapTo(violations) { Violation(relativePath, index + 1, it.value.trim()) }
        }
        return violations
    }

    private fun isZero(number: String) = number.all { it == '0' || it == '.' }

    /** Replaces string and comment contents with spaces, keeping line breaks so line numbers hold. */
    private fun blankLiteralsAndComments(text: String): String {
        val out = StringBuilder(text.length)
        var i = 0
        fun blank(c: Char) = if (c == '\n') '\n' else ' '
        while (i < text.length) {
            val c = text[i]
            when {
                text.startsWith("//", i) -> {
                    while (i < text.length && text[i] != '\n') { out.append(' '); i++ }
                }
                text.startsWith("/*", i) -> {
                    var depth = 0
                    while (i < text.length) {
                        if (text.startsWith("/*", i)) { depth++; out.append("  "); i += 2 }
                        else if (text.startsWith("*/", i)) { depth--; out.append("  "); i += 2; if (depth == 0) break }
                        else { out.append(blank(text[i])); i++ }
                    }
                }
                text.startsWith("\"\"\"", i) -> {
                    out.append("   "); i += 3
                    while (i < text.length && !text.startsWith("\"\"\"", i)) { out.append(blank(text[i])); i++ }
                    if (i < text.length) { out.append("   "); i += 3 }
                }
                c == '"' -> {
                    out.append(' '); i++
                    while (i < text.length && text[i] != '"' && text[i] != '\n') {
                        if (text[i] == '\\' && i + 1 < text.length) { out.append("  "); i += 2 } else { out.append(' '); i++ }
                    }
                    if (i < text.length && text[i] == '"') { out.append(' '); i++ }
                }
                c == '\'' && i + 2 < text.length && text[i + 1] != '\\' && text[i + 2] == '\'' -> {
                    out.append("   "); i += 3
                }
                else -> { out.append(c); i++ }
            }
        }
        return out.toString()
    }
}
