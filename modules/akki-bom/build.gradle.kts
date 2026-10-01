import akki.buildlogic.BOM_ARTIFACTS
import akki.buildlogic.mavenGroup

plugins {
    `java-platform`
    id("akki.publishing")
}

description = "Bill of materials that aligns the versions of akki artifacts"

dependencies {
    constraints {
        BOM_ARTIFACTS.forEach { api("$mavenGroup:$it:$version") }
    }
}
