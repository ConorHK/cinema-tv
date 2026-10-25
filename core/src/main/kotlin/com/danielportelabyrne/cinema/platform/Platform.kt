package com.danielportelabyrne.cinema.platform

import java.io.File

/**
 * What the shared code needs from the device it runs on. The TV implements it over an Android Context, the Linux
 * desktop over XDG folders and JSON files; nothing else in :core knows which one it is running on.
 */
interface Platform {
    /** Private storage for files that must stay (watch history, saved picks). */
    val filesDir: File

    /** Storage the system may clear (the catalogue cache). */
    val cacheDir: File

    /** What Jellyfin's dashboard calls this device, for example "Valve Steam Deck". */
    val deviceName: String

    /** The key-value file called [name]. Calls with the same name share their values. */
    fun preferences(name: String): Preferences

    /** Whether a preferences file called [name] has been saved (the TV's old catalogue cache used one). */
    fun hasPreferences(name: String): Boolean

    fun deletePreferences(name: String): Boolean

    /** Where passwords and API keys are kept, encrypted for this platform. */
    fun secretStore(): SecretStore
}
