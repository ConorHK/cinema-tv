/*
 * The code both Cinema apps share: the Jellyfin, Seerr, Sonarr/Radarr and torrent-client clients, Letterboxd parsing,
 * the catalogue, and the rules behind Watch now / Continue / Restart. It is plain Kotlin for the JVM and knows nothing
 * of Android or of the desktop: what it needs from the device is the small interface in the platform package, which
 * each app implements. Its tests run on any machine with a JDK, with no Android SDK.
 */
plugins {
    id("org.jetbrains.kotlin.jvm")
}

base {
    // Both apps put every jar on one class path; a bare "core.jar" would be a poor name there.
    archivesName.set("cinema-core")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // Android ships org.json; the desktop app adds the real thing itself.
    compileOnly("org.json:json:20231013")

    testImplementation("org.json:json:20231013")
    testImplementation("junit:junit:4.13.2")
}
