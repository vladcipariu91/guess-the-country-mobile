plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test"))
}

gradlePlugin {
    plugins {
        register("designTokens") {
            id = "gtc.design-tokens"
            implementationClass = "gtc.designtokens.DesignTokensPlugin"
        }
    }
}

tasks.test {
    useJUnit()
    // Tests read the real token file, so editing it must re-run them.
    inputs.file("../design/tokens.json").withPropertyName("tokensFile")
}
