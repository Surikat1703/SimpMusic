pluginManagement {
    repositories {
        // Fork: Maven Central, the Gradle Plugin Portal and dl.google.com are all unreliable from the
        // maintainer's network — Cloudflare-backed hosts never finish TLS, and dl.google.com resets
        // large downloads. Reachable proxies replace them: Huawei's mirror is fast and carries Maven
        // Central plus Google's Android artifacts, Tencent proxies the Gradle Plugin Portal, and
        // Google's own Maven Central mirror is the backup. The blocked repositories stay commented
        // out on purpose: keeping them makes Gradle retry every miss there at multi-minute timeouts.
        // Fork: the three jitpack jars Gradle refused to fetch through the proxy sit in this local
        // repository on the maintainer's machine. The directory does not exist in CI, so the line
        // is a no-op there and the normal repositories below keep serving those modules.
        File("E:/m2local").takeIf { it.isDirectory }?.let { maven(it.toURI()) }
        maven("https://repo.huaweicloud.com/repository/maven")
        maven("https://mirrors.cloud.tencent.com/nexus/repository/gradle-plugins/")
        maven("https://maven-central.storage-download.googleapis.com/maven2")
        maven("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
        // google()             // dl.google.com resets big downloads here — covered by the mirrors above
        // mavenCentral()       // unreachable here (Cloudflare) — replaced by the mirrors above
        // gradlePluginPortal() // unreachable here (Cloudflare) — replaced by the Tencent mirror above
        maven { setUrl("https://jitpack.io") }
        // oss.sonatype.org (legacy OSSRH) removed — Sonatype shut it down; its flaky
        // 504s disabled the whole repo set and blocked resolution fallbacks.
        maven("https://jogamp.org/deployment/maven")
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Fork: same reason as in pluginManagement above.
        // Fork: the three jitpack jars Gradle refused to fetch through the proxy sit in this local
        // repository on the maintainer's machine. The directory does not exist in CI, so the line
        // is a no-op there and the normal repositories below keep serving those modules.
        File("E:/m2local").takeIf { it.isDirectory }?.let { maven(it.toURI()) }
        maven("https://repo.huaweicloud.com/repository/maven")
        maven("https://mirrors.cloud.tencent.com/nexus/repository/gradle-plugins/")
        maven("https://maven-central.storage-download.googleapis.com/maven2")
        maven("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
        // google()             // dl.google.com resets big downloads here — covered by the mirrors above
        // mavenCentral()       // unreachable here (Cloudflare) — replaced by the mirrors above
        // gradlePluginPortal() // unreachable here (Cloudflare) — replaced by the Tencent mirror above
        maven { url = uri("https://jitpack.io") }
        maven("https://jogamp.org/deployment/maven")
        maven(url = "https://raw.githubusercontent.com/bravepipeproject/maven-repo/master/repository")
    }
}

// Foojay resolver disabled: plugins.gradle.org is unreachable on this network and JDK 21 is already installed locally.
// plugins {
//     id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
// }

// Core modules live in the `core` git submodule INSIDE this repo.
// We intentionally resolve ONLY the in-repo submodule and no longer probe a
// sibling `../core` outside SimpMusic: another project (FPT Play `core`) shares
// the same folder name one level up, and the old co-development lookup bound to
// it by mistake, breaking configuration with ":common ... does not exist".
val coreDir = File(rootDir, "core")
val serviceDir = File(rootDir, "core/service")
val mediaDir = File(rootDir, "core/media")

rootProject.name = "SimpMusic"
include(
    ":androidApp",
    ":vivoAndroidApp",
    ":composeApp",
    ":desktopApp",
    ":common",
    ":data",
    ":domain",
    ":ktorExt",
    ":kotlinYtmusicScraper",
    ":spotify",
    ":aiService",
    ":autoEqService",
    ":lyricsService",
    ":media-jvm",
    ":media-jvm-ui",
    ":media3",
    ":media3-ui",
    ":crashlytics",
    ":crashlytics-empty",
    ":cast",
    ":cast-empty",
    ":lastfm",
    ":lastfm-empty",
    ":kizzy",
    ":listenTogether",
    ":loginSync",
)

// core modules
project(":common").projectDir = File(coreDir, "common")
project(":data").projectDir = File(coreDir, "data")
project(":domain").projectDir = File(coreDir, "domain")

// service modules
project(":ktorExt").projectDir = File(serviceDir, "ktorExt")
project(":aiService").projectDir = File(serviceDir, "aiService")
project(":autoEqService").projectDir = File(serviceDir, "autoEqService")
project(":lyricsService").projectDir = File(serviceDir, "lyricsService")
project(":kotlinYtmusicScraper").projectDir = File(serviceDir, "kotlinYtmusicScraper")
project(":spotify").projectDir = File(serviceDir, "spotify")
project(":kizzy").projectDir = File(serviceDir, "kizzy")
project(":listenTogether").projectDir = File(serviceDir, "listenTogether")
project(":loginSync").projectDir = File(serviceDir, "loginSync")

// media modules
project(":media-jvm").projectDir = File(mediaDir, "media-jvm")
project(":media-jvm-ui").projectDir = File(mediaDir, "media-jvm-ui")
project(":media3").projectDir = File(mediaDir, "media3")
project(":media3-ui").projectDir = File(mediaDir, "media3-ui")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
