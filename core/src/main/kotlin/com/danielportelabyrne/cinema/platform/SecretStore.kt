package com.danielportelabyrne.cinema.platform

/**
 * Passwords and API keys that have to outlive a restart (a download client's password, a Sonarr key typed in by hand).
 * Each platform encrypts them its own way. Values are never logged. Every call is safe on any thread and none throw:
 * a value that can no longer be read simply reads as missing and has to be entered again.
 */
interface SecretStore {
    fun get(name: String): String?

    /** Saves [value], or forgets the name when it is null or blank. */
    fun put(name: String, value: String?)

    fun clear()
}
