package gtc.designtokens

import java.math.BigDecimal
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** The two files a Claude Design "Design System" project reads, derived from `tokens.json`. */
data class DesignSystemFiles(val tokensJson: String, val tokensCss: String)

/**
 * Re-expresses `design/tokens.json` in the Claude Design system grammar (a flat themed colour list,
 * type groups, and one `{"tokens": [...]}` list per other family) plus a matching stylesheet.
 * Pure: source text in, file contents out. Parsing and validation reuse [TokensCodegen]'s helpers.
 */
object DesignSystemExport {
    const val GENERATED_BY = ":shared:exportDesignSystem"
    const val SOURCE_PATH = "design/tokens.json"

    /** One font file bundled with the app: which role it serves and the CSS weight(s) it covers. */
    class FontFace(val role: String, val file: String, val weight: String)

    /** The real resource names. The extrabold file also renders 700, mirroring the app. */
    val fontFaces = listOf(
        FontFace("display", "bricolage_grotesque_regular.ttf", "400"),
        FontFace("display", "bricolage_grotesque_semibold.ttf", "600"),
        FontFace("display", "bricolage_grotesque_extrabold.ttf", "700 800"),
        FontFace("mono", "ibm_plex_mono_medium.ttf", "500"),
    )

    private val nameRule = Regex("^[A-Za-z0-9][A-Za-z0-9_.-]{0,63}$")
    private val camelBoundary = Regex("(?<=[a-z0-9])(?=[A-Z])")
    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    /** A non-colour, non-type token: its [name], CSS [value] and optional [usage]. */
    private class Flat(val name: String, val value: String, val usage: String?)

    private class Style(
        val name: String,
        val role: String,
        val size: String,
        val weight: String,
        val letterSpacing: String?,
        val lineHeight: String?,
        val uppercase: Boolean,
        val usage: String,
    )

    private class Colour(val name: String, val light: String, val dark: String, val usage: String)

    private val familyStacks = mapOf(
        "display" to "system-ui, sans-serif",
        "mono" to "ui-monospace, monospace",
    )

    fun export(json: String, title: String, sourceSha256: String): DesignSystemFiles {
        val root = TokensCodegen.parseRoot(json)
        val familyNames = fontFamilyNames(root)
        val stacks = familyStacks.mapValues { (role, fallback) -> "\"${familyNames.getValue(role)}\", $fallback" }

        val colours = colours(TokensCodegen.section(root, "color"))
        val styles = styles(TokensCodegen.section(root, "type"))
        val families = linkedMapOf(
            "spacing" to flat(root, "space", "space-", "px"),
            "radius" to flat(root, "radius", "radius-", "px"),
            "size" to flat(root, "size", "size-", "px"),
            "border" to flat(root, "border", "border-", "px"),
            "elevation" to flat(root, "elevation", "elevation-", "px"),
            "opacity" to flat(root, "opacity", "opacity-", ""),
            "dash" to flat(root, "dash", "dash-", "", usage = "multiple of the stroke width"),
            "tilt" to flat(root, "tilt", "tilt-", "deg"),
        )
        assertNamesUnique(colours.map { it.name to "color.${it.name}" } + families.values.flatten().map { it.name to it.name })
        styles.forEach { requireValidName(it.name, "type.${it.name}") }

        return DesignSystemFiles(
            tokensJson = tokensJson(title, sourceSha256, colours, styles, families, familyNames, stacks),
            tokensCss = tokensCss(title, colours, styles, families.values.flatten(), familyNames, stacks),
        )
    }

    // ---- reading ----

    private fun fontFamilyNames(root: JsonObject): Map<String, String> {
        val font = TokensCodegen.section(root, "font") ?: throw TokensException("font: missing section")
        return familyStacks.keys.associateWith { role ->
            val name = (font[role] as? JsonPrimitive)?.takeIf { it.isString }?.content
            if (name.isNullOrBlank() || '"' in name || '\\' in name) {
                throw TokensException("font.$role: expected a font family name")
            }
            name
        }
    }

    private fun colours(color: JsonObject?): List<Colour> = TokensCodegen.colorLeaves(color).map { leaf ->
        val value = leaf.value()
        val name = leaf.path.joinToString("-") { kebab(it) }
        requireValidName(name, leaf.dotted)
        val usage = usage(leaf.node, leaf.dotted) ?: throw TokensException("${leaf.dotted}.usage: missing")
        Colour(name, value.light.css(), value.dark.css(), usage)
    }

    private fun styles(type: JsonObject?): List<Style> = (type ?: JsonObject(emptyMap())).map { (key, value) ->
        val path = "type.$key"
        val style = value as? JsonObject ?: throw TokensException("$path: expected an object")
        val role = (style["font"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (role == null || role !in TokensCodegen.fontRoles) {
            throw TokensException("$path.font: unknown font \"$role\" (expected ${TokensCodegen.fontRoles.keys.joinToString()})")
        }
        TokensCodegen.intLiteral(style["weight"], "$path.weight")
        val uppercase = style["uppercase"]?.let { TokensCodegen.booleanLiteral(it, "$path.uppercase") } == "true"
        Style(
            name = kebab(key),
            role = role,
            size = plain(style["size"], "$path.size"),
            weight = plain(style["weight"], "$path.weight"),
            letterSpacing = style["letterSpacingEm"]?.let { plain(it, "$path.letterSpacingEm") }?.takeIf { it != "0" },
            lineHeight = style["lineHeight"]?.let { plain(it, "$path.lineHeight") },
            uppercase = uppercase,
            usage = listOfNotNull(usage(style, path), "Uppercase.".takeIf { uppercase }).joinToString(" "),
        )
    }

    private fun flat(root: JsonObject, section: String, prefix: String, unit: String, usage: String? = null): List<Flat> =
        (TokensCodegen.section(root, section) ?: JsonObject(emptyMap())).map { (key, value) ->
            val path = "$section.$key"
            val name = prefix + kebab(key)
            requireValidName(name, path)
            Flat(name, plain(value, path) + unit, usage)
        }

    private fun usage(node: JsonObject, path: String): String? {
        val element = node["usage"] ?: return null
        val text = (element as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (text.isNullOrBlank()) throw TokensException("$path.usage: expected a non-empty sentence")
        return text
    }

    /** `scoreDisplay` becomes `score-display`, `b1_5` becomes `b1-5`. */
    private fun kebab(key: String): String = key.replace('_', '-').replace(camelBoundary, "-").lowercase()

    private fun plain(element: JsonElement?, path: String): String =
        TokensCodegen.decimal(element, path).let { if (it.signum() == 0) "0" else it.stripTrailingZeros().toPlainString() }

    private fun requireValidName(name: String, path: String) {
        if (!nameRule.matches(name)) {
            throw TokensException("$path: invalid token name \"$name\" (must match ${nameRule.pattern})")
        }
    }

    private fun assertNamesUnique(named: List<Pair<String, String>>) {
        val seen = mutableSetOf<String>()
        for ((name, path) in named) {
            if (!seen.add(name)) throw TokensException("$path: duplicate token name \"$name\"")
        }
    }

    // ---- tokens.json ----

    private fun tokensJson(
        title: String,
        sha: String,
        colours: List<Colour>,
        styles: List<Style>,
        families: Map<String, List<Flat>>,
        familyNames: Map<String, String>,
        stacks: Map<String, String>,
    ): String {
        val root = buildJsonObject {
            put("name", title)
            put("version", 1)
            put("meta", buildJsonObject {
                put("source", "code")
                put("path", SOURCE_PATH)
                put("sha256", sha)
                put("generatedBy", GENERATED_BY)
            })
            put("color", buildJsonObject {
                put("themes", buildJsonArray {
                    add(theme("light", "Light"))
                    add(theme("dark", "Dark"))
                })
                put("tokens", buildJsonArray {
                    colours.forEach { c ->
                        add(buildJsonObject {
                            put("name", c.name)
                            put("value", buildJsonObject {
                                put("light", c.light)
                                put("dark", c.dark)
                            })
                            put("usage", c.usage)
                        })
                    }
                })
            })
            put("type", buildJsonObject {
                put("fonts", buildJsonArray {
                    fontFaces.forEach { f ->
                        add(buildJsonObject {
                            put("family", familyNames.getValue(f.role))
                            put("file", "fonts/${f.file}")
                            put("weight", f.weight)
                        })
                    }
                })
                put("families", buildJsonObject { stacks.forEach { (role, stack) -> put(role, stack) } })
                put("groups", buildJsonArray {
                    add(group("Display", "display", styles))
                    add(group("Labels", "mono", styles))
                })
            })
            families.forEach { (family, tokens) ->
                put(family, buildJsonObject {
                    put("tokens", buildJsonArray {
                        tokens.forEach { t ->
                            add(buildJsonObject {
                                put("name", t.name)
                                put("value", t.value)
                                t.usage?.let { put("usage", it) }
                            })
                        }
                    })
                })
            }
        }
        return json.encodeToString(JsonObject.serializer(), root) + "\n"
    }

    private fun theme(id: String, name: String) = buildJsonObject {
        put("id", id)
        put("name", name)
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun group(name: String, role: String, styles: List<Style>): JsonObject = buildJsonObject {
        put("name", name)
        put("family", role)
        put("styles", JsonArray(styles.filter { it.role == role }.map { s ->
            buildJsonObject {
                put("name", s.name)
                put("fontSize", "${s.size}px")
                put("fontWeight", JsonUnquotedLiteral(s.weight))
                s.letterSpacing?.let { put("letterSpacing", "${it}em") }
                s.lineHeight?.let { put("lineHeight", JsonUnquotedLiteral(it)) }
                put("usage", s.usage)
            }
        }))
    }

    // ---- tokens.css ----

    private fun tokensCss(
        title: String,
        colours: List<Colour>,
        styles: List<Style>,
        flats: List<Flat>,
        familyNames: Map<String, String>,
        stacks: Map<String, String>,
    ): String {
        val blocks = mutableListOf<String>()
        blocks += "/* $title — generated from tokens.json */"
        blocks += rule(":root, [data-theme=\"light\"]", colours.map { "--${it.name}: ${it.light};" })
        val dark = colours.filter { it.dark != it.light }
        if (dark.isNotEmpty()) blocks += rule("[data-theme=\"dark\"]", dark.map { "--${it.name}: ${it.dark};" })
        blocks += rule(
            ":root",
            flats.map { "--${it.name}: ${it.value};" } + stacks.map { (role, stack) -> "--font-$role: $stack;" },
        )
        styles.forEach { s ->
            blocks += rule(
                ".${s.name}",
                listOfNotNull(
                    "font-family: var(--font-${s.role});",
                    "font-size: ${s.size}px;",
                    "font-weight: ${s.weight};",
                    s.letterSpacing?.let { "letter-spacing: ${it}em;" },
                    s.lineHeight?.let { "line-height: $it;" },
                    "text-transform: uppercase;".takeIf { s.uppercase },
                ),
            )
        }
        fontFaces.forEach { f ->
            blocks += rule(
                "@font-face",
                listOf(
                    "font-family: \"${familyNames.getValue(f.role)}\";",
                    "src: url(\"fonts/${f.file}\") format(\"truetype\");",
                    "font-weight: ${f.weight};",
                    "font-style: normal;",
                ),
            )
        }
        return blocks.joinToString("\n\n") + "\n"
    }

    private fun rule(selector: String, declarations: List<String>): String =
        "$selector {\n" + declarations.joinToString("") { "  $it\n" } + "}"
}
