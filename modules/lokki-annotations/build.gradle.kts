plugins {
    id("lokki.kotlin-multiplatform")
}

kotlin {
    explicitApi()

    androidNativeArm32()
    androidNativeArm64()
    androidNativeX64()
    androidNativeX86()

    iosArm64()
    iosSimulatorArm64()
    iosX64()

    jvm()

    linuxArm64()
    linuxX64()

    macosArm64()

    mingwX64()

    tvosArm64()
    tvosSimulatorArm64()

    watchosArm32()
    watchosArm64()
    watchosDeviceArm64()
    watchosSimulatorArm64()

    applyDefaultHierarchyTemplate()

    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation()
}
