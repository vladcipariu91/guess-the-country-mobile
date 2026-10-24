package gtc.designtokens

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RawValueScannerTest {
    private fun scan(text: String) = RawValueScanner.scan("Screen.kt", text.trimIndent())

    @Test
    fun `flags dp and sp literals`() {
        val found = scan(
            """
            Modifier.padding(16.dp)
            Text(fontSize = 14.sp)
            val gap = 2.5.dp
            val big = 120.sp
            """,
        )
        assertEquals(listOf(1, 2, 3, 4), found.map { it.line })
        assertEquals(listOf("16.dp", "14.sp", "2.5.dp", "120.sp"), found.map { it.match })
        assertTrue(found.all { it.path == "Screen.kt" })
    }

    @Test
    fun `allows zero dp`() {
        assertTrue(scan("Modifier.padding(0.dp)").isEmpty())
    }

    @Test
    fun `ignores numbers inside string literals and comments`() {
        val found = scan(
            """
            Text("12/21 STAMPS")
            val a = "16.dp"
            // was 16.dp
            /* 14.sp
               still 14.sp */
            val raw = ${"\"\"\""}
                8.dp
            ${"\"\"\""}
            """,
        )
        assertTrue(found.isEmpty(), found.toString())
    }

    @Test
    fun `flags colour literals and font construction`() {
        val found = scan(
            """
            val c = Color(0xFF5E1A26)
            val f = FontFamily(
            val g = Font(Res.font.x)
            """,
        )
        assertEquals(listOf(1, 2, 3), found.map { it.line })
    }

    @Test
    fun `flags direct material theme reads`() {
        val found = scan(
            """
            val c = MaterialTheme.colorScheme.primary
            val t = MaterialTheme.typography.titleLarge
            """,
        )
        assertEquals(2, found.size)
    }

    @Test
    fun `code after a closed block comment on the same line is still scanned`() {
        assertEquals(1, scan("/* note */ val x = 4.dp").size)
    }
}
