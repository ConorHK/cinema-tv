package com.danielportelabyrne.cinema.data

import java.net.HttpURLConnection

inline fun <T> HttpURLConnection.exchange(block: () -> T): T {
    val result = try { block() } catch (e: Throwable) { disconnect(); throw e }
    try { (errorStream ?: inputStream).use { it.readBytes() } } catch (_: Exception) { }
    return result
}
