package com.danielportelabyrne.cinema.desktop

import java.io.File

/**
 * Where Cinema keeps its files on a desktop, following the XDG base directories. Inside the Flatpak these point into
 * the app's own sandbox folder (~/.var/app/<app id>/...). [root] can be overridden with CINEMA_HOME, which tests and
 * screenshot runs use to stay away from a real sign-in.
 */
object AppDirs {
    private fun xdg(name: String, fallback: String): File {
        System.getenv("CINEMA_HOME")?.takeIf { it.isNotBlank() }?.let { return File(it, fallback.substringAfterLast('/')) }
        val base = System.getenv(name)?.takeIf { it.isNotBlank() }?.let(::File) ?: File(System.getProperty("user.home"), fallback)
        return File(base, "cinema")
    }

    val data: File by lazy { xdg("XDG_DATA_HOME", ".local/share").apply { mkdirs() } }
    val cache: File by lazy { xdg("XDG_CACHE_HOME", ".cache").apply { mkdirs() } }
    val runtime: File by lazy {
        val dir = System.getenv("XDG_RUNTIME_DIR")?.takeIf { it.isNotBlank() }?.let { File(it, "cinema") } ?: File(cache, "run")
        dir.apply { mkdirs() }
    }

    /** Makes a file readable and writable by its owner only (it may hold a sign-in token). */
    fun ownerOnly(file: File) {
        file.setReadable(false, false); file.setReadable(true, true)
        file.setWritable(false, false); file.setWritable(true, true)
    }
}
