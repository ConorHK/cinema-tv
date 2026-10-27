package com.danielportelabyrne.cinema.data.music.cast

import java.io.ByteArrayInputStream
import org.junit.Assert.*
import org.junit.Test

class UpnpTest {
    private val didl = """<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/"
        xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/"><item id="1" parentID="0" restricted="1">
        <dc:title>Song &amp; Dance</dc:title><dc:creator>Someone</dc:creator><upnp:artist>Some Band</upnp:artist>
        <upnp:album>An Album</upnp:album><upnp:albumArtURI>http://192.168.1.20:4533/rest/getCoverArt?id=al-1</upnp:albumArtURI>
        <upnp:class>object.item.audioItem.musicTrack</upnp:class>
        <res duration="0:03:21.500" protocolInfo="http-get:*:audio/flac:*">http://192.168.1.20:4533/rest/stream?id=1</res>
        </item></DIDL-Lite>"""

    @Test fun reads_the_track_from_didl() {
        val item = Upnp.item("http://x/stream", didl)
        assertEquals("Song & Dance", item.title)
        assertEquals("Some Band", item.artist)
        assertEquals("An Album", item.album)
        assertEquals("http://192.168.1.20:4533/rest/getCoverArt?id=al-1", item.artUri)
        assertEquals(201_500L, item.durationMs)
    }

    @Test fun bad_metadata_still_plays() {
        val item = Upnp.item("http://x/stream", "not xml")
        assertEquals("http://x/stream", item.uri)
        assertNull(item.title)
    }

    @Test fun parses_a_soap_action() {
        val body = """<?xml version="1.0"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body>
            <u:Seek xmlns:u="urn:schemas-upnp-org:service:AVTransport:1"><InstanceID>0</InstanceID><Unit>REL_TIME</Unit>
            <Target>0:01:05</Target></u:Seek></s:Body></s:Envelope>"""
        assertEquals(SoapAction("Seek", mapOf("InstanceID" to "0", "Unit" to "REL_TIME", "Target" to "0:01:05")), Upnp.parseAction(body))
    }

    @Test fun refuses_doctypes() {
        val evil = """<?xml version="1.0"?><!DOCTYPE x [<!ENTITY e SYSTEM "file:///etc/passwd">]>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body><u:Play><Speed>&e;</Speed></u:Play></s:Body></s:Envelope>"""
        assertNull(Upnp.parseAction(evil))
    }

    @Test fun clocks_round_trip() {
        assertEquals("1:02:03", Upnp.clock(3_723_000))
        assertEquals(3_723_000L, Upnp.parseClock("1:02:03"))
        assertEquals(65_250L, Upnp.parseClock("00:01:05.250"))
        assertNull(Upnp.parseClock("65"))
    }

    @Test fun reads_requests_with_length_or_chunks() {
        val plain = "POST /AVTransport/control HTTP/1.1\r\nContent-Length: 5\r\nSOAPACTION: \"x#Play\"\r\n\r\nhello"
        val r = HttpRequest.read(ByteArrayInputStream(plain.toByteArray()))!!
        assertEquals("POST", r.method); assertEquals("hello", r.body); assertEquals("\"x#Play\"", r.header("soapaction"))
        val chunked = "POST / HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n3\r\nhel\r\n2\r\nlo\r\n0\r\n\r\n"
        assertEquals("hello", HttpRequest.read(ByteArrayInputStream(chunked.toByteArray()))!!.body)
    }

    @Test fun answers_searches_for_this_device_only() {
        val search = "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: ${Upnp.DEVICE_TYPE}\r\n\r\n"
        assertEquals(Upnp.DEVICE_TYPE, Ssdp.searchTarget(search))
        assertNull(Ssdp.searchTarget("NOTIFY * HTTP/1.1\r\nNT: upnp:rootdevice\r\n\r\n"))
        assertEquals(listOf(Upnp.DEVICE_TYPE to "uuid:u1::${Upnp.DEVICE_TYPE}"), Ssdp.targets(Upnp.DEVICE_TYPE, "u1"))
        assertEquals(6, Ssdp.targets(Ssdp.ALL, "u1").size)
        assertTrue(Ssdp.targets("urn:schemas-upnp-org:device:MediaServer:1", "u1").isEmpty())
    }

    @Test fun every_service_has_a_document() {
        for (s in Upnp.SERVICES) assertTrue(Upnp.scpd(s)!!.contains("<actionList>"))
        assertTrue(Upnp.description("u1", "Cinema & Co").contains("<friendlyName>Cinema &amp; Co</friendlyName>"))
    }
}
