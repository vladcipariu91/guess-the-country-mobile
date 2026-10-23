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

/** Registers `generateDesignTokens`, which writes Kotlin to `build/generated/designTokens/kotlin`. */
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
    }
}
