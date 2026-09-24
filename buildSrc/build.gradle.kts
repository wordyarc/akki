plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kotlin.power.assert.gradle.plugin)
    implementation(libs.kotlin.allopen.gradle.plugin)
    implementation(libs.kotlinx.benchmark.gradle.plugin)
    implementation(libs.kover.gradle.plugin)
    implementation(libs.maven.publish.gradle.plugin)
    implementation(libs.plugin.publish.gradle.plugin)
}
