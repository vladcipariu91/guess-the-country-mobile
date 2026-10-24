package gtc.designtokens

import java.security.MessageDigest
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Derives the Claude Design system project from `tokens.json`: `tokens.json`, `tokens.css` and
 * `fonts/<ttf>` in [outputDir]. Nothing is published here.
 */
@CacheableTask
abstract class ExportDesignSystemTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val tokensFile: RegularFileProperty

    /** The font files the app ships; copied by file name, so the name is part of the input. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val fontFiles: ConfigurableFileCollection

    @get:Input
    abstract val title: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun export() {
        val source = tokensFile.get().asFile
        val sha = MessageDigest.getInstance("SHA-256")
            .digest(source.readBytes())
            .joinToString("") { "%02x".format(it) }
        val files = DesignSystemExport.export(source.readText(), title.get(), sha)

        val root = outputDir.get().asFile
        root.deleteRecursively()
        val fonts = root.resolve("fonts").apply { mkdirs() }
        root.resolve("tokens.json").writeText(files.tokensJson)
        root.resolve("tokens.css").writeText(files.tokensCss)
        for (face in DesignSystemExport.fontFaces) {
            val font = fontFiles.files.firstOrNull { it.name == face.file }
                ?: throw GradleException("Font file ${face.file} is missing from the task inputs")
            font.copyTo(fonts.resolve(face.file))
        }
    }
}
