package gtc.designtokens

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesignSystemExportTest {
    private val title = "Guess the Country — Passport"
    private val sha = "abc123"

    private val fonts = """"font":{"display":"Bricolage Grotesque","mono":"IBM Plex Mono"}"""

    private fun export(json: String) = DesignSystemExport.export(json, title, sha)

    private fun tokens(json: String): JsonObject = Json.parseToJsonElement(export(json).tokensJson).jsonObject

    private fun JsonObject.obj(key: String) = getValue(key).jsonObject

    private fun JsonObject.arr(key: String) = getValue(key).jsonArray

    private fun JsonArray.byName(name: String) = first { it.jsonObject.getValue("name").jsonPrimitive.content == name }.jsonObject

    private val twoColours = """
        {"color":{
          "cover":{"navy":{"light":"#16213D","usage":"Cover."}},
          "gold":{"light":"#D4A85A","dark":"#E8C27E","usage":"Accent."}
        }, $fonts}
    """.trimIndent()

    @Test
    fun `colours become one flat themed list`() {
        val color = tokens(twoColours).obj("color")
        val themes = color.arr("themes")
        assertEquals(listOf("light", "dark"), themes.map { it.jsonObject.getValue("id").jsonPrimitive.content })
        assertEquals(listOf("Light", "Dark"), themes.map { it.jsonObject.getValue("name").jsonPrimitive.content })
        val list = color.arr("tokens")
        assertEquals(listOf("cover-navy", "gold"), list.map { it.jsonObject.getValue("name").jsonPrimitive.content })
        val navy = list.byName("cover-navy")
        assertEquals("#16213d", navy.obj("value").getValue("light").jsonPrimitive.content)
        assertEquals("#16213d", navy.obj("value").getValue("dark").jsonPrimitive.content)
        assertEquals("Cover.", navy.getValue("usage").jsonPrimitive.content)
        assertEquals("#e8c27e", list.byName("gold").obj("value").getValue("dark").jsonPrimitive.content)
    }

    @Test
    fun `camel case colour keys become kebab case`() {
        val list = tokens(
            """{"color":{"goldLight":{"light":"#E8C27E","usage":"u"},"stamp":{"tier5":{"light":"#7A5A12","usage":"u"}}},$fonts}""",
        ).obj("color").arr("tokens")
        assertEquals(listOf("gold-light", "stamp-tier5"), list.map { it.jsonObject.getValue("name").jsonPrimitive.content })
    }

    @Test
    fun `alpha keeps css order lowercase`() {
        val list = tokens(
            """{"color":{"onCoverDisabled":{"light":"#F4EFE399","usage":"Dimmed."}},$fonts}""",
        ).obj("color").arr("tokens")
        assertEquals("#f4efe399", list.byName("on-cover-disabled").obj("value").getValue("light").jsonPrimitive.content)
    }

    @Test
    fun `missing usage names the path`() {
        val e = assertFailsWith<TokensException> {
            export("""{"color":{"gold":{"light":"#D4A85A"}},$fonts}""")
        }
        assertContains(e.message.orEmpty(), "color.gold")
    }

    @Test
    fun `invalid colour names the path`() {
        val e = assertFailsWith<TokensException> {
            export("""{"color":{"gold":{"light":"gold","usage":"u"}},$fonts}""")
        }
        assertContains(e.message.orEmpty(), "color.gold.light")
    }

    private val styles = """
        "type":{
          "scoreDisplay":{"font":"display","size":64,"weight":800,"letterSpacingEm":-0.03,"lineHeight":0.9,"usage":"Big score."},
          "monoTag":{"font":"mono","size":11,"weight":500,"letterSpacingEm":0.16,"uppercase":true},
          "body":{"font":"display","size":15,"weight":400}
        }
    """.trimIndent()

    @Test
    fun `type styles map to groups by font`() {
        val type = tokens("""{$fonts,$styles}""").obj("type")
        val groups = type.arr("groups")
        assertEquals(listOf("Display", "Labels"), groups.map { it.jsonObject.getValue("name").jsonPrimitive.content })
        val display = groups[0].jsonObject
        val labels = groups[1].jsonObject
        assertEquals("display", display.getValue("family").jsonPrimitive.content)
        assertEquals("mono", labels.getValue("family").jsonPrimitive.content)
        val displayStyles = display.arr("styles")
        assertEquals(listOf("score-display", "body"), displayStyles.map { it.jsonObject.getValue("name").jsonPrimitive.content })
        val score = displayStyles.byName("score-display")
        assertEquals("64px", score.getValue("fontSize").jsonPrimitive.content)
        assertEquals("800", score.getValue("fontWeight").jsonPrimitive.content)
        assertEquals("-0.03em", score.getValue("letterSpacing").jsonPrimitive.content)
        assertEquals("0.9", score.getValue("lineHeight").jsonPrimitive.content)
        assertEquals("Big score.", score.getValue("usage").jsonPrimitive.content)
        val body = displayStyles.byName("body")
        assertFalse("letterSpacing" in body)
        assertFalse("lineHeight" in body)
        assertEquals("", body.getValue("usage").jsonPrimitive.content)
        val tag = labels.arr("styles").byName("mono-tag")
        assertEquals("Uppercase.", tag.getValue("usage").jsonPrimitive.content)
        assertEquals("0.16em", tag.getValue("letterSpacing").jsonPrimitive.content)
    }

    @Test
    fun `uppercase is appended to an existing usage`() {
        val type = tokens(
            """{$fonts,"type":{"brandTitle":{"font":"display","size":24,"weight":800,"uppercase":true,"usage":"Brand."}}}""",
        ).obj("type")
        val style = type.arr("groups")[0].jsonObject.arr("styles")[0].jsonObject
        assertEquals("Brand. Uppercase.", style.getValue("usage").jsonPrimitive.content)
    }

    @Test
    fun `type style with unknown font names the path`() {
        val e = assertFailsWith<TokensException> {
            export("""{$fonts,"type":{"body":{"font":"serif","size":15,"weight":400}}}""")
        }
        assertContains(e.message.orEmpty(), "type.body.font")
    }

    @Test
    fun `extrabold face covers 700 and 800`() {
        val faces = tokens("""{$fonts}""").obj("type").arr("fonts")
        assertEquals(4, faces.size)
        val extra = faces.first { it.jsonObject.getValue("file").jsonPrimitive.content.endsWith("extrabold.ttf") }.jsonObject
        assertEquals("700 800", extra.getValue("weight").jsonPrimitive.content)
        assertEquals("Bricolage Grotesque", extra.getValue("family").jsonPrimitive.content)
        assertEquals("fonts/bricolage_grotesque_extrabold.ttf", extra.getValue("file").jsonPrimitive.content)
        val mono = faces.last().jsonObject
        assertEquals("IBM Plex Mono", mono.getValue("family").jsonPrimitive.content)
        assertEquals("fonts/ibm_plex_mono_medium.ttf", mono.getValue("file").jsonPrimitive.content)
        assertEquals("500", mono.getValue("weight").jsonPrimitive.content)
        val families = tokens("""{$fonts}""").obj("type").obj("families")
        assertEquals("\"Bricolage Grotesque\", system-ui, sans-serif", families.getValue("display").jsonPrimitive.content)
        assertEquals("\"IBM Plex Mono\", ui-monospace, monospace", families.getValue("mono").jsonPrimitive.content)
    }

    @Test
    fun `other families use css values`() {
        val root = tokens(
            """{$fonts,"space":{"s4":4},"radius":{"full":999},"size":{"touchMin":44},"border":{"b1_5":1.5},""" +
                """"elevation":{"passport":14},"opacity":{"dimmedFlag":0.6},"dash":{"lengthFactor":2},"tilt":{"usedStamp":-6},""" +
                """"motion":{"loadingBarMillis":3000}}""",
        )
        fun value(family: String, name: String) =
            root.obj(family).arr("tokens").byName(name).getValue("value").jsonPrimitive.content
        assertEquals("4px", value("spacing", "space-s4"))
        assertEquals("999px", value("radius", "radius-full"))
        assertEquals("44px", value("size", "size-touch-min"))
        assertEquals("1.5px", value("border", "border-b1-5"))
        assertEquals("14px", value("elevation", "elevation-passport"))
        assertEquals("0.6", value("opacity", "opacity-dimmed-flag"))
        assertEquals("2", value("dash", "dash-length-factor"))
        assertEquals("-6deg", value("tilt", "tilt-used-stamp"))
        assertEquals(
            "multiple of the stroke width",
            root.obj("dash").arr("tokens").byName("dash-length-factor").getValue("usage").jsonPrimitive.content,
        )
        assertFalse("motion" in root)
    }

    @Test
    fun `top level carries the source metadata`() {
        val root = tokens("""{$fonts}""")
        assertEquals(title, root.getValue("name").jsonPrimitive.content)
        assertEquals("1", root.getValue("version").jsonPrimitive.content)
        val meta = root.obj("meta")
        assertEquals("code", meta.getValue("source").jsonPrimitive.content)
        assertEquals("design/tokens.json", meta.getValue("path").jsonPrimitive.content)
        assertEquals(sha, meta.getValue("sha256").jsonPrimitive.content)
        assertEquals(":shared:exportDesignSystem", meta.getValue("generatedBy").jsonPrimitive.content)
    }

    @Test
    fun `names are unique across families`() {
        val e = assertFailsWith<TokensException> {
            export(
                """{"color":{"space-s4":{"light":"#000000","usage":"u"}},$fonts,"space":{"s4":4}}""",
            )
        }
        assertContains(e.message.orEmpty(), "space-s4")
    }

    @Test
    fun `names must be valid`() {
        val longKey = "a".repeat(70)
        val e = assertFailsWith<TokensException> {
            export("""{"space":{"$longKey":4},$fonts}""")
        }
        assertContains(e.message.orEmpty(), "space.$longKey")
    }

    @Test
    fun `names are unique and valid across families in the real file`() {
        val root = tokens(File("../design/tokens.json").readText())
        val valid = Regex("^[A-Za-z0-9][A-Za-z0-9_.-]{0,63}$")
        val names = mutableListOf<String>()
        for ((key, family) in root) {
            if (key == "name" || key == "version" || key == "meta" || key == "type") continue
            val list = family.jsonObject.arr("tokens")
            names += list.map { it.jsonObject.getValue("name").jsonPrimitive.content }
        }
        assertTrue(names.isNotEmpty())
        assertEquals(names.size, names.toSet().size, "names are unique")
        names.forEach { assertTrue(valid.matches(it), "$it is a valid name") }
    }

    @Test
    fun `css has first theme on root and a class per style`() {
        val css = export(
            """{"color":{"gold":{"light":"#D4A85A","dark":"#E8C27E","usage":"u"},"ink":{"light":"#1B1B1B","usage":"u"}},$fonts,""" +
                """"space":{"s4":4},$styles}""",
        ).tokensCss
        val lines = css.lines()
        assertEquals("/* $title — generated from tokens.json */", lines[0])
        assertContains(css, ":root, [data-theme=\"light\"] {\n  --gold: #d4a85a;\n  --ink: #1b1b1b;\n}")
        assertContains(css, "[data-theme=\"dark\"] {\n  --gold: #e8c27e;\n}")
        assertContains(css, "--space-s4: 4px;")
        assertContains(css, "--font-display: \"Bricolage Grotesque\", system-ui, sans-serif;")
        assertContains(css, "--font-mono: \"IBM Plex Mono\", ui-monospace, monospace;")
        assertContains(
            css,
            ".score-display {\n  font-family: var(--font-display);\n  font-size: 64px;\n  font-weight: 800;\n" +
                "  letter-spacing: -0.03em;\n  line-height: 0.9;\n}",
        )
        assertContains(css, "text-transform: uppercase;")
        assertContains(
            css,
            "@font-face {\n  font-family: \"Bricolage Grotesque\";\n" +
                "  src: url(\"fonts/bricolage_grotesque_extrabold.ttf\") format(\"truetype\");\n" +
                "  font-weight: 700 800;\n  font-style: normal;\n}",
        )
    }

    @Test
    fun `dark block is omitted when no colour differs`() {
        val css = export(
            """{"color":{"ink":{"light":"#1B1B1B","usage":"u"}},$fonts}""",
        ).tokensCss
        assertFalse("[data-theme=\"dark\"]" in css)
    }

    @Test
    fun `real tokens file exports`() {
        val source = File("../design/tokens.json").readText()
        val files = DesignSystemExport.export(source, title, sha)
        val root = Json.parseToJsonElement(files.tokensJson).jsonObject
        assertTrue(root.obj("color").arr("tokens").size > 30)
        root.obj("color").arr("tokens").forEach {
            assertTrue(it.jsonObject.getValue("usage").jsonPrimitive.content.isNotBlank())
        }
        assertEquals(
            Json.parseToJsonElement(source).jsonObject.obj("type").size,
            root.obj("type").arr("groups").sumOf { it.jsonObject.arr("styles").size },
        )
        assertContains(files.tokensCss, ".score-display {")
        assertContains(files.tokensCss, "--stamp-tier5: #7a5a12;")
    }
}
