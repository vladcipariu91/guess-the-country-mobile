package gtc.designtokens

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/** Generates `DesignTokens.kt` from `tokens.json` into [outputDir]. */
@CacheableTask
abstract class GenerateDesignTokensTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val tokensFile: RegularFileProperty

    @get:Input
    abstract val packageName: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val source = TokensCodegen.generate(tokensFile.get().asFile.readText(), packageName.get())
        val root = outputDir.get().asFile
        root.deleteRecursively()
        val dir = root.resolve(packageName.get().replace('.', '/'))
        dir.mkdirs()
        dir.resolve("DesignTokens.kt").writeText(source)
    }
}
