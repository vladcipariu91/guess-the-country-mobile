package gtc.designtokens

import java.math.BigDecimal
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Thrown when `tokens.json` is malformed; the message names the offending token path. */
class TokensException(message: String) : RuntimeException(message)

/** Turns the contents of `design/tokens.json` into one Kotlin source file of `internal` token objects. */
object TokensCodegen {
    internal val fontRoles = mapOf("display" to "Display", "mono" to "Mono")
    private val keyPattern = Regex("[A-Za-z][A-Za-z0-9_]*")
    private val hexPattern = Regex("#([0-9A-Fa-f]{6})([0-9A-Fa-f]{2})?")

    fun generate(json: String, packageName: String): String {
        val root = parseRoot(json)

        return buildString {
            appendLine("// GENERATED from design/tokens.json by :shared:generateDesignTokens. Do not edit.")
            appendLine("@file:Suppress(\"unused\")")
            appendLine()
            appendLine("package $packageName")
            appendLine()
            appendLine("import androidx.compose.ui.graphics.Color")
            appendLine("import androidx.compose.ui.unit.Dp")
            appendLine("import androidx.compose.ui.unit.dp")
            appendLine()
            appendLine("internal class ColorToken(val light: Color, val dark: Color)")
            appendLine()
            appendLine("internal enum class FontRole { ${fontRoles.values.joinToString(", ")} }")
            appendLine()
            appendLine("internal class TypeToken(")
            appendLine("    val font: FontRole,")
            appendLine("    val sizeSp: Float,")
            appendLine("    val weight: Int,")
            appendLine("    val letterSpacingEm: Float,")
            appendLine("    val lineHeight: Float?,")
            appendLine("    val uppercase: Boolean,")
            appendLine(")")
            appendLine()
            appendObject("ColorTokens", colorEntries(section(root, "color")), allType = "ColorToken")
            appendObject("TypeTokens", typeEntries(section(root, "type")), allType = "TypeToken")
            appendObject("Space", dpEntries(root, "space"), allType = "Dp")
            appendObject("Size", dpEntries(root, "size"), allType = "Dp")
            appendObject("Radius", dpEntries(root, "radius"), allType = "Dp")
            appendObject("Border", dpEntries(root, "border"), allType = "Dp")
            appendObject("Elevation", dpEntries(root, "elevation"))
            appendObject("Dash", floatEntries(root, "dash"))
            appendObject("Tilt", floatEntries(root, "tilt"))
            appendObject("Opacity", floatEntries(root, "opacity"))
            appendObject("Motion", motionEntries(section(root, "motion")), last = true)
        }
    }

    /** Parses the top-level object of `tokens.json`; shared with [DesignSystemExport]. */
    internal fun parseRoot(json: String): JsonObject = try {
        Json.parseToJsonElement(json) as? JsonObject
            ?: throw TokensException("tokens: expected a JSON object at the top level")
    } catch (e: kotlinx.serialization.SerializationException) {
        throw TokensException("tokens: invalid JSON (${e.message})")
    }

    private val declaredName = Regex("^val (\\w+) =")

    /**
     * Emits `internal object [name]`. With [allType] it also gets `val All`, a list pairing each
     * token's property name with its value, so a catalogue can show every token without a hand copy.
     */
    private fun StringBuilder.appendObject(
        name: String,
        entries: List<String>,
        last: Boolean = false,
        allType: String? = null,
    ) {
        appendLine("internal object $name {")
        entries.forEach { appendLine("    $it") }
        if (allType != null) {
            val names = entries.map { declaredName.find(it)!!.groupValues[1] }
            appendLine("    val All: List<Pair<String, $allType>> = listOf(")
            names.forEach { appendLine("        \"$it\" to $it,") }
            appendLine("    )")
        }
        appendLine("}")
        if (!last) appendLine()
    }

    internal fun section(root: JsonObject, name: String): JsonObject? {
        val element = root[name] ?: return null
        return element as? JsonObject ?: throw TokensException("$name: expected an object")
    }

    private fun colorEntries(color: JsonObject?): List<String> {
        val entries = colorLeaves(color).map { leaf ->
            val value = leaf.value()
            pascal(leaf.path, "color") to
                "ColorToken(light = ${composeColor(value.light)}, dark = ${composeColor(value.dark)})"
        }
        return declare(entries, "color")
    }

    /** A colour token found under `color`: its key [path] and the `{light, dark?, ...}` object. */
    internal class ColorLeaf(val path: List<String>, val node: JsonObject) {
        val dotted: String get() = "color." + path.joinToString(".")

        fun value(): ColorValue {
            if ("light" !in node) throw TokensException("$dotted: missing \"light\"")
            val light = parseHex(node["light"], "$dotted.light")
            val dark = node["dark"]?.let { parseHex(it, "$dotted.dark") } ?: light
            return ColorValue(light, dark)
        }
    }

    internal class ColorValue(val light: Hex, val dark: Hex)

    /** `#RRGGBB` or `#RRGGBBAA` (CSS order) split into upper-case [rgb] and optional [alpha]. */
    internal class Hex(val rgb: String, val alpha: String?) {
        /** Lower-case CSS text, e.g. `#f4efe399`. */
        fun css(): String = "#" + (rgb + (alpha ?: "")).lowercase()
    }

    /** Walks `color` depth-first, in file order, and returns every leaf colour token. */
    internal fun colorLeaves(color: JsonObject?): List<ColorLeaf> {
        val leaves = mutableListOf<ColorLeaf>()
        fun walk(node: JsonObject, path: List<String>) {
            for ((key, value) in node) {
                val childPath = path + key
                val dotted = "color." + childPath.joinToString(".")
                val obj = value as? JsonObject ?: throw TokensException("$dotted: expected an object")
                // A node with no children or any primitive child is meant as a colour, not a group.
                val isLeaf = "light" in obj || obj.isEmpty() || obj.values.any { it is JsonPrimitive }
                if (isLeaf) leaves += ColorLeaf(childPath, obj) else walk(obj, childPath)
            }
        }
        if (color != null) walk(color, emptyList())
        return leaves
    }

    internal fun parseHex(element: JsonElement?, path: String): Hex {
        val text = (element as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: throw TokensException("$path: expected a colour string like \"#RRGGBB\"")
        val match = hexPattern.matchEntire(text)
            ?: throw TokensException("$path: invalid colour \"$text\" (expected #RRGGBB or #RRGGBBAA)")
        return Hex(match.groupValues[1].uppercase(), match.groupValues[2].uppercase().ifEmpty { null })
    }

    private fun composeColor(hex: Hex): String = "Color(0x${hex.alpha ?: "FF"}${hex.rgb})"

    private fun typeEntries(type: JsonObject?): List<String> {
        val entries = type.orEmpty().map { (key, value) ->
            val path = "type.$key"
            val style = value as? JsonObject ?: throw TokensException("$path: expected an object")
            val fontKey = (style["font"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            val role = fontRoles[fontKey]
                ?: throw TokensException("$path.font: unknown font \"${fontKey}\" (expected ${fontRoles.keys.joinToString()})")
            val size = floatLiteral(style["size"], "$path.size")
            val weight = intLiteral(style["weight"], "$path.weight")
            val spacing = style["letterSpacingEm"]?.let { floatLiteral(it, "$path.letterSpacingEm") } ?: "0f"
            val lineHeight = style["lineHeight"]?.let { floatLiteral(it, "$path.lineHeight") } ?: "null"
            val uppercase = style["uppercase"]?.let { booleanLiteral(it, "$path.uppercase") } ?: "false"
            pascal(listOf(key), "type") to
                "TypeToken(font = FontRole.$role, sizeSp = $size, weight = $weight, " +
                "letterSpacingEm = $spacing, lineHeight = $lineHeight, uppercase = $uppercase)"
        }
        return declare(entries, "type")
    }

    private fun dpEntries(root: JsonObject, name: String): List<String> {
        val entries = section(root, name).orEmpty().map { (key, value) ->
            pascal(listOf(key), name) to "${number(value, "$name.$key")}.dp"
        }
        return declare(entries, name)
    }

    /** Unitless values that are always `Float`, e.g. rotation in degrees or an alpha from 0 to 1. */
    private fun floatEntries(root: JsonObject, name: String): List<String> {
        val entries = section(root, name).orEmpty().map { (key, value) ->
            pascal(listOf(key), name) to "${floatLiteral(value, "$name.$key")}"
        }
        return declare(entries, name).map { it.replaceFirst("val ", "const val ").replaceFirst(" =", ": Float =") }
    }

    private fun motionEntries(motion: JsonObject?): List<String> =
        motion.orEmpty().map { (key, value) ->
            val path = "motion.$key"
            val text = number(value, path)
            val name = pascal(listOf(key), "motion")
            if ('.' in text) "const val $name: Float = ${text}f" else "const val $name: Int = $text"
        }

    /** Renders `val Name = value` lines, rejecting duplicate property names. */
    private fun declare(entries: List<Pair<String, String>>, section: String): List<String> {
        val seen = mutableSetOf<String>()
        return entries.map { (name, value) ->
            if (!seen.add(name)) throw TokensException("$section: duplicate property name $name")
            "val $name = $value"
        }
    }

    /** `["cover", "burgundy"]` becomes `CoverBurgundy`; underscores inside a key are kept. */
    private fun pascal(path: List<String>, section: String): String {
        path.forEach {
            if (!keyPattern.matches(it)) throw TokensException("$section.$it: key is not a valid identifier")
        }
        return path.joinToString("") { it.replaceFirstChar(Char::uppercaseChar) }
    }

    /** The plain decimal text of a JSON number, e.g. `2`, `1.5`, `-0.03`. */
    private fun number(element: JsonElement?, path: String): String = decimal(element, path).toPlainString()

    internal fun decimal(element: JsonElement?, path: String): BigDecimal {
        val primitive = element as? JsonPrimitive
        return primitive?.takeIf { !it.isString }?.content?.toBigDecimalOrNull()
            ?: throw TokensException("$path: expected a number")
    }

    private fun floatLiteral(element: JsonElement?, path: String): String = number(element, path) + "f"

    internal fun intLiteral(element: JsonElement?, path: String): String {
        val text = number(element, path)
        if (BigDecimal(text).scale() > 0 || text.toIntOrNull() == null) {
            throw TokensException("$path: expected an integer")
        }
        return text
    }

    internal fun booleanLiteral(element: JsonElement, path: String): String {
        val primitive = element as? JsonPrimitive
        val value = primitive?.takeIf { !it.isString }?.content
        if (value != "true" && value != "false") throw TokensException("$path: expected true or false")
        return value
    }

    private fun JsonObject?.orEmpty(): JsonObject = this ?: JsonObject(emptyMap())
}
