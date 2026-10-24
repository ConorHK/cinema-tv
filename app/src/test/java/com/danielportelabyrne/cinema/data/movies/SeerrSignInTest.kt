package com.danielportelabyrne.cinema.data.movies

import org.junit.Assert.*
import org.junit.Test

class SeerrSignInTest {
    @Test fun successful_login_saves_the_session_and_does_not_report_a_missing_session() {
        val saved = mutableListOf<String>()
        val headers: Map<String?, List<String>> = mapOf("Set-Cookie" to listOf("connect.sid=s%3Atest-session; Path=/; HttpOnly"))
        val error = SeerrApi.finishSignIn(200, headers) { saved += it }
        assertNull(error)
        assertEquals(listOf("connect.sid=s%3Atest-session"), saved)
    }

    @Test fun lower_case_headers_and_other_cookies_still_complete_sign_in() {
        var session: String? = null
        val headers: Map<String?, List<String>> = mapOf("set-cookie" to listOf("other=value; Path=/", "connect.sid=session; HttpOnly; Path=/"))
        assertNull(SeerrApi.finishSignIn(200, headers) { session = it })
        assertEquals("connect.sid=session", session)
    }

    @Test fun a_response_without_a_session_reports_an_error_without_saving_anything() {
        var saved = false
        val headers: Map<String?, List<String>> = mapOf("Set-Cookie" to listOf("other=value; Path=/"))
        assertEquals("Signed in, but no session came back", SeerrApi.finishSignIn(200, headers) { saved = true })
        assertFalse(saved)
        assertNotNull(SeerrApi.finishSignIn(200, null) { saved = true })
        assertFalse(saved)
    }

    @Test fun rejected_or_failed_logins_never_save_a_session() {
        var saved = false
        val headers: Map<String?, List<String>> = mapOf("Set-Cookie" to listOf("connect.sid=ignored; Path=/"))
        for (code in listOf(401, 403)) {
            assertEquals("Wrong username or password", SeerrApi.finishSignIn(code, headers) { saved = true })
        }
        assertEquals("Couldn't sign in (HTTP 500)", SeerrApi.finishSignIn(500, headers) { saved = true })
        assertFalse(saved)
    }
}
