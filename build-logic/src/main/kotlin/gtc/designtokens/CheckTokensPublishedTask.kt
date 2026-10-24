package gtc.designtokens

import java.security.MessageDigest
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/** Fails when `tokens.json` differs from the version last published to Claude Design. */
abstract class CheckTokensPublishedTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val tokensFile: RegularFileProperty

    /** Holds the SHA-256 of the published `tokens.json` as its first whitespace-separated token. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val publishedHashFile: RegularFileProperty

    @TaskAction
    fun check() {
        val actual = MessageDigest.getInstance("SHA-256")
            .digest(tokensFile.get().asFile.readBytes())
            .joinToString("") { "%02x".format(it) }
        val published = publishedHashFile.get().asFile.readText().trim().split(Regex("\\s+")).first()
        if (!actual.equals(published, ignoreCase = true)) {
            throw GradleException(
                "design/tokens.json changed since it was last published to Claude Design. " +
                    "Republish the design-system project, then write the new hash to " +
                    "design/tokens.published.sha256 (see design/README.md).",
            )
        }
    }
}
