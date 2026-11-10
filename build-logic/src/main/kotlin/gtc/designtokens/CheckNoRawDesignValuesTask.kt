package gtc.designtokens

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Fails when screen code under [sources] uses raw design values (see [RawValueScanner]).
 * Files under `/designsystem/` are exempt; files listed in [baseline] are tolerated until
 * they are clean, at which point their entry must be removed.
 */
abstract class CheckNoRawDesignValuesTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val baseline: RegularFileProperty

    /** Directory that baseline entries and reported paths are relative to. */
    @get:Internal
    abstract val repoRoot: DirectoryProperty

    @TaskAction
    fun check() {
        val root = repoRoot.get().asFile
        val tolerated = baseline.get().asFile.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toSet()

        val byFile = sources.files
            .map { it.relativeTo(root).invariantSeparatorsPath to it }
            .filter { (path, _) -> !path.contains("/designsystem/") }
            .sortedBy { it.first }
            .associate { (path, file) -> path to RawValueScanner.scan(path, file.readText()) }

        val unexpected = byFile.filterKeys { it !in tolerated }.values.flatten()
        val stale = tolerated.filter { byFile[it].isNullOrEmpty() }.sorted()

        if (unexpected.isEmpty() && stale.isEmpty()) return
        val message = buildString {
            if (unexpected.isNotEmpty()) {
                appendLine("Raw design values found; use design system tokens instead:")
                unexpected.forEach { appendLine("  ${it.path}:${it.line} ${it.match}") }
            }
            if (stale.isNotEmpty()) {
                appendLine("Baseline entries with no raw values left; remove them from ${baseline.get().asFile.name}:")
                stale.forEach { appendLine("  $it") }
            }
        }
        throw GradleException(message.trimEnd())
    }
}
