plugins {
    id("gradlebuild.internal.java")
    application
}

repositories {
    google()
}

dependencies {
    implementation(project(":base-services"))
    implementation(project(":tooling-api"))
    api(libs.agp)
}

application {
    mainClass.set("org.gradle.performance.android.Main")
    applicationName = "android-test-app"
}

listOf(tasks.distZip, tasks.distTar).forEach {
    it { archiveBaseName.set("android-test-app") }
}

tasks.register("buildDists") {
    dependsOn(tasks.distZip)
}
