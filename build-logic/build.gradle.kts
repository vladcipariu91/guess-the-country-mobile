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
}
