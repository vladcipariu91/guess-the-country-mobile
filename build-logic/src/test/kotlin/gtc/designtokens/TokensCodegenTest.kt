package gtc.designtokens

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TokensCodegenTest {
    private val pkg = "me.example.tokens"

    private fun generate(json: String) = TokensCodegen.generate(json, pkg)

    @Test
    fun `rgb hex becomes opaque compose colour`() {
        val out = generate("""{"color":{"cover":{"burgundy":{"light":"#5E1A26"}}}}""")
        assertContains(out, "Color(0xFF5E1A26)")
    }

    @Test
    fun `css alpha hex is reordered to argb`() {
        val out = generate("""{"color":{"onCoverDisabled":{"light":"#F4EFE399"}}}""")
        assertContains(out, "Color(0x99F4EFE3)")
    }

    @Test
    fun `dark falls back to light`() {
        val out = generate("""{"color":{"paper":{"light":"#F4EFE3"}}}""")
        assertContains(out, "ColorToken(light = Color(0xFFF4EFE3), dark = Color(0xFFF4EFE3))")
    }

    @Test
    fun `explicit dark is used`() {
        val out = generate("""{"color":{"paper":{"light":"#F4EFE3","dark":"#101010"}}}""")
        assertContains(out, "ColorToken(light = Color(0xFFF4EFE3), dark = Color(0xFF101010))")
    }

    @Test
    fun `nested keys become pascal case`() {
        val out = generate(
            """{"color":{"cover":{"burgundy":{"light":"#5E1A26"}}},"border":{"b1_5":1.5,"b2":2}}""",
        )
        assertContains(out, "val CoverBurgundy")
        assertContains(out, "val B1_5 = 1.5.dp")
        assertContains(out, "val B2 = 2.dp")
    }

    @Test
    fun `motion ints and floats keep their type`() {
        val out = generate("""{"motion":{"stampSlamMillis":250,"stampSlamStartScale":1.4}}""")
        assertContains(out, "const val StampSlamMillis: Int = 250")
        assertContains(out, "const val StampSlamStartScale: Float = 1.4f")
    }

    @Test
    fun `dash tilt and opacity sections are emitted`() {
        val out = generate("""{"dash":{"lengthFactor":2,"gapFactor":1},"tilt":{"usedStamp":-6},"opacity":{"dimmedFlag":0.6}}""")
        assertContains(out, "internal object Dash {")
        assertContains(out, "const val LengthFactor: Float = 2f")
        assertContains(out, "const val GapFactor: Float = 1f")
        assertContains(out, "const val UsedStamp: Float = -6f")
        assertContains(out, "const val DimmedFlag: Float = 0.6f")
    }

    @Test
    fun `all lists every entry of each listed section`() {
        val out = generate(
            """{"color":{"cover":{"navy":{"light":"#16213D"}},"gold":{"light":"#D4A85A"}},""" +
                """"type":{"body":{"font":"display","size":15,"weight":400},"monoTag":{"font":"mono","size":11,"weight":500}},""" +
                """"space":{"s2":2,"s4":4},"size":{"tile":76},"radius":{"r4":4,"full":999},"border":{"b1":1,"b1_5":1.5}}""",
        )
        assertContains(out, "val All: List<Pair<String, ColorToken>> = listOf(\n        \"CoverNavy\" to CoverNavy,\n        \"Gold\" to Gold,\n    )")
        assertContains(out, "val All: List<Pair<String, TypeToken>> = listOf(\n        \"Body\" to Body,\n        \"MonoTag\" to MonoTag,\n    )")
        assertContains(out, "val All: List<Pair<String, Dp>> = listOf(\n        \"S2\" to S2,\n        \"S4\" to S4,\n    )")
        assertContains(out, "\"Tile\" to Tile,")
        assertContains(out, "\"R4\" to R4,\n        \"Full\" to Full,")
        assertContains(out, "\"B1\" to B1,\n        \"B1_5\" to B1_5,")
    }

    @Test
    fun `all matches every declared token in the real file`() {
        val out = generate(File("../design/tokens.json").readText())
        for (objectName in listOf("ColorTokens", "TypeTokens", "Space", "Size", "Radius", "Border")) {
            val body = out.substringAfter("internal object $objectName {").substringBefore("\n}\n")
            val declared = Regex("(?m)^    val (\\w+) = ").findAll(body).map { it.groupValues[1] }.toList()
            val listed = Regex("(?m)^        \"(\\w+)\" to \\1,").findAll(body).map { it.groupValues[1] }.toList()
            assertTrue(declared.isNotEmpty(), "$objectName has tokens")
            assertEquals(declared, listed, "$objectName.All lists every token in order")
        }
    }

    @Test
    fun `type defaults are applied`() {
        val out = generate(
            """{"type":{"caption":{"font":"display","size":13,"weight":400}}}""",
        )
        assertContains(
            out,
            "val Caption = TypeToken(font = FontRole.Display, sizeSp = 13f, weight = 400, " +
                "letterSpacingEm = 0f, lineHeight = null, uppercase = false)",
        )
    }

    @Test
    fun `type values are emitted`() {
        val out = generate(
            """{"type":{"monoTag":{"font":"mono","size":11,"weight":500,"letterSpacingEm":0.16,"lineHeight":1.4,"uppercase":true}}}""",
        )
        assertContains(
            out,
            "val MonoTag = TypeToken(font = FontRole.Mono, sizeSp = 11f, weight = 500, " +
                "letterSpacingEm = 0.16f, lineHeight = 1.4f, uppercase = true)",
        )
    }

    @Test
    fun `invalid hex names the token path`() {
        val e = assertFailsWith<TokensException> {
            generate("""{"color":{"gold":{"light":"#12"}}}""")
        }
        assertContains(e.message.orEmpty(), "color.gold")
    }

    @Test
    fun `dark only colour reports missing light`() {
        val e = assertFailsWith<TokensException> {
            generate("""{"color":{"gold":{"dark":"#000000"}}}""")
        }
        assertContains(e.message.orEmpty(), "color.gold")
        assertContains(e.message.orEmpty(), "missing \"light\"")
    }

    @Test
    fun `misspelled light key reports missing light`() {
        val e = assertFailsWith<TokensException> {
            generate("""{"color":{"gold":{"ligth":"#D4A85A"}}}""")
        }
        assertContains(e.message.orEmpty(), "color.gold")
        assertContains(e.message.orEmpty(), "missing \"light\"")
    }

    @Test
    fun `empty colour object names its path`() {
        val e = assertFailsWith<TokensException> {
            generate("""{"color":{"cover":{"navy":{}}}}""")
        }
        assertContains(e.message.orEmpty(), "color.cover.navy")
        assertContains(e.message.orEmpty(), "missing \"light\"")
    }

    @Test
    fun `type style with unknown font names the path`() {
        val e = assertFailsWith<TokensException> {
            generate("""{"type":{"body":{"font":"serif","size":15,"weight":400}}}""")
        }
        assertContains(e.message.orEmpty(), "type.body")
    }

    @Test
    fun `non numeric dimension names the path`() {
        val e = assertFailsWith<TokensException> {
            generate("""{"space":{"s2":"two"}}""")
        }
        assertContains(e.message.orEmpty(), "space.s2")
    }

    @Test
    fun `invalid json is a tokens exception`() {
        assertFailsWith<TokensException> { generate("{") }
    }

    @Test
    fun `real tokens file generates`() {
        val json = File("../design/tokens.json").readText()
        val out = generate(json)
        assertContains(out, "val StampTier5")
        assertContains(out, "package $pkg")
        assertTrue(out.startsWith("// GENERATED from design/tokens.json"))
    }
}
