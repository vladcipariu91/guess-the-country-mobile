package gtc.designtokens

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property

/** `designTokens { tokensFile; packageName }` configuration. */
abstract class DesignTokensExtension {
    abstract val tokensFile: RegularFileProperty
    abstract val packageName: Property<String>
}

/**
 * Registers `generateDesignTokens` (Kotlin into `build/generated/designTokens/kotlin`) plus the
 * `checkNoRawDesignValues` and `checkTokensPublished` checks, and `exportDesignSystem`, which is
 * deliberately not part of `check`/`assemble`/`build` (a later step publishes its output). The checks read `design/` at the
 * repository root; the caller wires them into `check`.
 */
class DesignTokensPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("designTokens", DesignTokensExtension::class.java)
        project.tasks.register("generateDesignTokens", GenerateDesignTokensTask::class.java) {
            group = "build"
            description = "Generates Kotlin design tokens from tokens.json."
            tokensFile.set(extension.tokensFile)
            packageName.set(extension.packageName)
            outputDir.set(project.layout.buildDirectory.dir("generated/designTokens/kotlin"))
        }

        project.tasks.register("exportDesignSystem", ExportDesignSystemTask::class.java) {
            group = "build"
            description = "Writes tokens.json, tokens.css and fonts/ in the Claude Design system format."
            tokensFile.set(extension.tokensFile)
            fontFiles.from(DesignSystemExport.fontFaces.map { project.layout.projectDirectory.file("$FONT_DIR/${it.file}") })
            title.set("Guess the Country — Passport")
            outputDir.set(project.layout.buildDirectory.dir("designSystem/project"))
        }

        val rootDir = project.rootProject.layout.projectDirectory
        project.tasks.register("checkNoRawDesignValues", CheckNoRawDesignValuesTask::class.java) {
            group = "verification"
            description = "Fails when screen code uses raw dp/sp, colour or font values."
            sources.from(project.fileTree("src/commonMain/kotlin") { include("**/*.kt") })
            baseline.set(rootDir.file("design/raw-values-baseline.txt"))
            repoRoot.set(rootDir)
        }
        project.tasks.register("checkTokensPublished", CheckTokensPublishedTask::class.java) {
            group = "verification"
            description = "Fails when tokens.json changed since it was published to Claude Design."
            tokensFile.set(extension.tokensFile)
            publishedHashFile.set(rootDir.file("design/tokens.published.sha256"))
        }
    }

    private companion object {
        const val FONT_DIR = "src/commonMain/composeResources/font"
    }
}
