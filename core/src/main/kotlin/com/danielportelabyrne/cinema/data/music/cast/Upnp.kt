package com.danielportelabyrne.cinema.data.music.cast

import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource

/** A track a controller asked to play: its stream address and what its DIDL-Lite metadata says. */
data class CastItem(
    val uri: String, val metadata: String, val title: String?, val artist: String?, val album: String?,
    val artUri: String?, val durationMs: Long?
)

/** One SOAP call: the action's name and its arguments. */
data class SoapAction(val name: String, val args: Map<String, String>)

/** The XML a UPnP media renderer speaks: its description, SOAP bodies, DIDL-Lite metadata and events. */
object Upnp {
    const val DEVICE_TYPE = "urn:schemas-upnp-org:device:MediaRenderer:1"
    const val AV_TRANSPORT = "urn:schemas-upnp-org:service:AVTransport:1"
    const val RENDERING_CONTROL = "urn:schemas-upnp-org:service:RenderingControl:1"
    const val CONNECTION_MANAGER = "urn:schemas-upnp-org:service:ConnectionManager:1"
    val SERVICES = listOf(AV_TRANSPORT, RENDERING_CONTROL, CONNECTION_MANAGER)

    /** Formats ExoPlayer plays, with the FFmpeg decoder covering the rest. */
    val SINK_PROTOCOLS = listOf(
        "audio/mpeg", "audio/mp3", "audio/flac", "audio/x-flac", "audio/ogg", "audio/opus", "audio/aac", "audio/mp4",
        "audio/x-m4a", "audio/wav", "audio/x-wav", "audio/L16", "audio/x-aiff", "audio/x-ape", "audio/x-ms-wma"
    ).joinToString(",") { "http-get:*:$it:*" }

    /** The short name in each service's URLs, e.g. `/AVTransport/control`. */
    fun shortName(serviceType: String): String = serviceType.removePrefix("urn:schemas-upnp-org:service:").substringBefore(':')

    fun description(uuid: String, friendlyName: String): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        append("<root xmlns=\"urn:schemas-upnp-org:device-1-0\" xmlns:dlna=\"urn:schemas-dlna-org:device-1-0\">")
        append("<specVersion><major>1</major><minor>0</minor></specVersion><device>")
        append("<deviceType>$DEVICE_TYPE</deviceType>")
        append("<friendlyName>${escape(friendlyName)}</friendlyName>")
        append("<manufacturer>Cinema</manufacturer><modelName>Cinema</modelName><modelNumber>1</modelNumber>")
        append("<UDN>uuid:$uuid</UDN><dlna:X_DLNADOC>DMR-1.50</dlna:X_DLNADOC><serviceList>")
        for (type in SERVICES) {
            val name = shortName(type)
            append("<service><serviceType>$type</serviceType><serviceId>urn:upnp-org:serviceId:$name</serviceId>")
            append("<SCPDURL>/$name.xml</SCPDURL><controlURL>/$name/control</controlURL><eventSubURL>/$name/event</eventSubURL></service>")
        }
        append("</serviceList></device></root>")
    }

    /** What [serviceType] offers, as its SCPD document. */
    fun scpd(serviceType: String): String? {
        val (actions, vars) = when (serviceType) {
            AV_TRANSPORT -> Scpd.avTransport
            RENDERING_CONTROL -> Scpd.renderingControl
            CONNECTION_MANAGER -> Scpd.connectionManager
            else -> return null
        }
        return buildString {
            append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<scpd xmlns=\"urn:schemas-upnp-org:service-1-0\">")
            append("<specVersion><major>1</major><minor>0</minor></specVersion><actionList>")
            for ((name, args) in actions) {
                append("<action><name>$name</name><argumentList>")
                for ((arg, dir, variable) in args) {
                    append("<argument><name>$arg</name><direction>$dir</direction><relatedStateVariable>$variable</relatedStateVariable></argument>")
                }
                append("</argumentList></action>")
            }
            append("</actionList><serviceStateTable>")
            for ((name, type, events) in vars) {
                append("<stateVariable sendEvents=\"${if (events) "yes" else "no"}\"><name>$name</name><dataType>$type</dataType></stateVariable>")
            }
            append("</serviceStateTable></scpd>")
        }
    }

    /** The action in a SOAP request body, or null when it is not one. */
    fun parseAction(body: String): SoapAction? {
        val doc = parse(body) ?: return null
        val soapBody = children(doc.documentElement).firstOrNull { local(it) == "Body" } ?: return null
        val action = children(soapBody).firstOrNull() ?: return null
        return SoapAction(local(action), children(action).associate { local(it) to it.textContent.orEmpty() })
    }

    fun response(serviceType: String, action: String, out: List<Pair<String, String>>): String = buildString {
        append(ENVELOPE_START)
        append("<u:${action}Response xmlns:u=\"$serviceType\">")
        for ((k, v) in out) append("<$k>${escape(v)}</$k>")
        append("</u:${action}Response>")
        append(ENVELOPE_END)
    }

    fun fault(code: Int, description: String): String =
        ENVELOPE_START + "<s:Fault><faultcode>s:Client</faultcode><faultstring>UPnPError</faultstring><detail>" +
            "<UPnPError xmlns=\"urn:schemas-upnp-org:control-1-0\"><errorCode>$code</errorCode>" +
            "<errorDescription>${escape(description)}</errorDescription></UPnPError></detail></s:Fault>" + ENVELOPE_END

    /** [uri] with what its DIDL-Lite [metadata] says about it. Bad metadata leaves the fields empty. */
    fun item(uri: String, metadata: String): CastItem {
        val item = parse(metadata)?.documentElement?.let { root -> children(root).firstOrNull { local(it) == "item" } }
        fun field(name: String) = item?.let { children(it).firstOrNull { c -> local(c) == name }?.textContent?.trim()?.ifEmpty { null } }
        val res = item?.let { children(it).firstOrNull { c -> local(c) == "res" } }
        return CastItem(
            uri, metadata, field("title"), field("artist") ?: field("creator"), field("album"), field("albumArtURI"),
            res?.getAttribute("duration")?.let { parseClock(it) }
        )
    }

    /** `H:MM:SS` as UPnP writes times. */
    fun clock(ms: Long): String {
        val s = ms.coerceAtLeast(0) / 1000
        return "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60)
    }

    /** Milliseconds in a UPnP time such as `0:03:21` or `00:03:21.500`, or null when it is not one. */
    fun parseClock(text: String): Long? {
        val parts = text.trim().split(':')
        if (parts.size != 3) return null
        val h = parts[0].toLongOrNull() ?: return null
        val m = parts[1].toLongOrNull() ?: return null
        val sec = parts[2].toDoubleOrNull() ?: return null
        return (h * 3600 + m * 60) * 1000 + (sec * 1000).toLong()
    }

    /** A GENA event body carrying [lastChange]. */
    fun propertySet(properties: List<Pair<String, String>>): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<e:propertyset xmlns:e=\"urn:schemas-upnp-org:event-1-0\">")
        for ((k, v) in properties) append("<e:property><$k>${escape(v)}</$k></e:property>")
        append("</e:propertyset>")
    }

    /** The LastChange document for one service: [values] are variable name to value, all on instance 0. */
    fun lastChange(serviceType: String, values: List<Pair<String, String>>): String {
        val ns = if (serviceType == RENDERING_CONTROL) "urn:schemas-upnp-org:metadata-1-0/RCS/" else "urn:schemas-upnp-org:metadata-1-0/AVT/"
        return buildString {
            append("<Event xmlns=\"$ns\"><InstanceID val=\"0\">")
            for ((k, v) in values) {
                val channel = if (serviceType == RENDERING_CONTROL) " channel=\"Master\"" else ""
                append("<$k$channel val=\"${escape(v)}\"/>")
            }
            append("</InstanceID></Event>")
        }
    }

    fun escape(text: String): String = buildString(text.length) {
        for (c in text) when (c) {
            '&' -> append("&amp;"); '<' -> append("&lt;"); '>' -> append("&gt;"); '"' -> append("&quot;"); '\'' -> append("&apos;")
            else -> append(c)
        }
    }

    private const val ENVELOPE_START = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
        "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body>"
    private const val ENVELOPE_END = "</s:Body></s:Envelope>"

    private val factory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        // Controllers are strangers on the network: no outside entities.
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        isExpandEntityReferences = false
    }

    private fun parse(xml: String) = if (xml.isBlank()) null else runCatching {
        synchronized(factory) { factory.newDocumentBuilder() }.parse(InputSource(StringReader(xml.trim())))
    }.getOrNull()

    private fun local(e: Element): String = e.localName ?: e.tagName.substringAfter(':')

    private fun children(e: Element): List<Element> {
        val nodes = e.childNodes
        return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
    }
}

/** Each service's actions (name to in/out arguments) and state variables (name, type, evented). */
private object Scpd {
    data class Arg(val name: String, val direction: String, val variable: String)
    data class Var(val name: String, val type: String, val events: Boolean = false)

    private fun i(name: String, variable: String) = Arg(name, "in", variable)
    private fun o(name: String, variable: String) = Arg(name, "out", variable)
    private val id = i("InstanceID", "A_ARG_TYPE_InstanceID")

    val avTransport: Pair<List<Pair<String, List<Arg>>>, List<Var>> = listOf(
        "SetAVTransportURI" to listOf(id, i("CurrentURI", "AVTransportURI"), i("CurrentURIMetaData", "AVTransportURIMetaData")),
        "SetNextAVTransportURI" to listOf(id, i("NextURI", "NextAVTransportURI"), i("NextURIMetaData", "NextAVTransportURIMetaData")),
        "GetMediaInfo" to listOf(
            id, o("NrTracks", "NumberOfTracks"), o("MediaDuration", "CurrentMediaDuration"), o("CurrentURI", "AVTransportURI"),
            o("CurrentURIMetaData", "AVTransportURIMetaData"), o("NextURI", "NextAVTransportURI"),
            o("NextURIMetaData", "NextAVTransportURIMetaData"), o("PlayMedium", "PlaybackStorageMedium"),
            o("RecordMedium", "RecordStorageMedium"), o("WriteStatus", "RecordMediumWriteStatus")
        ),
        "GetTransportInfo" to listOf(
            id, o("CurrentTransportState", "TransportState"), o("CurrentTransportStatus", "TransportStatus"),
            o("CurrentSpeed", "TransportPlaySpeed")
        ),
        "GetPositionInfo" to listOf(
            id, o("Track", "CurrentTrack"), o("TrackDuration", "CurrentTrackDuration"), o("TrackMetaData", "CurrentTrackMetaData"),
            o("TrackURI", "CurrentTrackURI"), o("RelTime", "RelativeTimePosition"), o("AbsTime", "AbsoluteTimePosition"),
            o("RelCount", "RelativeCounterPosition"), o("AbsCount", "AbsoluteCounterPosition")
        ),
        "GetDeviceCapabilities" to listOf(
            id, o("PlayMedia", "PossiblePlaybackStorageMedia"), o("RecMedia", "PossibleRecordStorageMedia"),
            o("RecQualityModes", "PossibleRecordQualityModes")
        ),
        "GetTransportSettings" to listOf(id, o("PlayMode", "CurrentPlayMode"), o("RecQualityMode", "CurrentRecordQualityMode")),
        "GetCurrentTransportActions" to listOf(id, o("Actions", "CurrentTransportActions")),
        "Stop" to listOf(id),
        "Play" to listOf(id, i("Speed", "TransportPlaySpeed")),
        "Pause" to listOf(id),
        "Seek" to listOf(id, i("Unit", "A_ARG_TYPE_SeekMode"), i("Target", "A_ARG_TYPE_SeekTarget")),
        "Next" to listOf(id),
        "Previous" to listOf(id),
    ) to listOf(
        Var("TransportState", "string"), Var("TransportStatus", "string"), Var("PlaybackStorageMedium", "string"),
        Var("RecordStorageMedium", "string"), Var("PossiblePlaybackStorageMedia", "string"),
        Var("PossibleRecordStorageMedia", "string"), Var("CurrentPlayMode", "string"), Var("TransportPlaySpeed", "string"),
        Var("RecordMediumWriteStatus", "string"), Var("CurrentRecordQualityMode", "string"),
        Var("PossibleRecordQualityModes", "string"), Var("NumberOfTracks", "ui4"), Var("CurrentTrack", "ui4"),
        Var("CurrentTrackDuration", "string"), Var("CurrentMediaDuration", "string"), Var("CurrentTrackMetaData", "string"),
        Var("CurrentTrackURI", "string"), Var("AVTransportURI", "string"), Var("AVTransportURIMetaData", "string"),
        Var("NextAVTransportURI", "string"), Var("NextAVTransportURIMetaData", "string"),
        Var("RelativeTimePosition", "string"), Var("AbsoluteTimePosition", "string"), Var("RelativeCounterPosition", "i4"),
        Var("AbsoluteCounterPosition", "i4"), Var("CurrentTransportActions", "string"), Var("LastChange", "string", events = true),
        Var("A_ARG_TYPE_SeekMode", "string"), Var("A_ARG_TYPE_SeekTarget", "string"), Var("A_ARG_TYPE_InstanceID", "ui4"),
    )

    val renderingControl: Pair<List<Pair<String, List<Arg>>>, List<Var>> = listOf(
        "ListPresets" to listOf(id, o("CurrentPresetNameList", "PresetNameList")),
        "SelectPreset" to listOf(id, i("PresetName", "A_ARG_TYPE_PresetName")),
        "GetVolume" to listOf(id, i("Channel", "A_ARG_TYPE_Channel"), o("CurrentVolume", "Volume")),
        "SetVolume" to listOf(id, i("Channel", "A_ARG_TYPE_Channel"), i("DesiredVolume", "Volume")),
        "GetMute" to listOf(id, i("Channel", "A_ARG_TYPE_Channel"), o("CurrentMute", "Mute")),
        "SetMute" to listOf(id, i("Channel", "A_ARG_TYPE_Channel"), i("DesiredMute", "Mute")),
    ) to listOf(
        Var("PresetNameList", "string"), Var("Volume", "ui2"), Var("Mute", "boolean"), Var("LastChange", "string", events = true),
        Var("A_ARG_TYPE_Channel", "string"), Var("A_ARG_TYPE_InstanceID", "ui4"), Var("A_ARG_TYPE_PresetName", "string"),
    )

    val connectionManager: Pair<List<Pair<String, List<Arg>>>, List<Var>> = listOf(
        "GetProtocolInfo" to listOf(o("Source", "SourceProtocolInfo"), o("Sink", "SinkProtocolInfo")),
        "GetCurrentConnectionIDs" to listOf(o("ConnectionIDs", "CurrentConnectionIDs")),
        "GetCurrentConnectionInfo" to listOf(
            i("ConnectionID", "A_ARG_TYPE_ConnectionID"), o("RcsID", "A_ARG_TYPE_RcsID"),
            o("AVTransportID", "A_ARG_TYPE_AVTransportID"), o("ProtocolInfo", "A_ARG_TYPE_ProtocolInfo"),
            o("PeerConnectionManager", "A_ARG_TYPE_ConnectionManager"), o("PeerConnectionID", "A_ARG_TYPE_ConnectionID"),
            o("Direction", "A_ARG_TYPE_Direction"), o("Status", "A_ARG_TYPE_ConnectionStatus")
        ),
    ) to listOf(
        Var("SourceProtocolInfo", "string", events = true), Var("SinkProtocolInfo", "string", events = true),
        Var("CurrentConnectionIDs", "string", events = true), Var("A_ARG_TYPE_ConnectionStatus", "string"),
        Var("A_ARG_TYPE_ConnectionManager", "string"), Var("A_ARG_TYPE_Direction", "string"),
        Var("A_ARG_TYPE_ProtocolInfo", "string"), Var("A_ARG_TYPE_ConnectionID", "i4"), Var("A_ARG_TYPE_AVTransportID", "i4"),
        Var("A_ARG_TYPE_RcsID", "i4"),
    )
}
