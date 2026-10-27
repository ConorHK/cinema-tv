package com.danielportelabyrne.cinema.data.music.cast

enum class Transport(val upnp: String) {
    NO_MEDIA("NO_MEDIA_PRESENT"), STOPPED("STOPPED"), PLAYING("PLAYING"), PAUSED("PAUSED_PLAYBACK"), TRANSITIONING("TRANSITIONING")
}

/** What the renderer is doing right now, as a controller asks for it. */
data class CastSnapshot(
    val transport: Transport, val current: CastItem?, val next: CastItem?, val positionMs: Long, val durationMs: Long,
    val volume: Int, val muted: Boolean
)

/** The player behind the renderer. Calls come from network threads. */
interface CastControl {
    fun snapshot(): CastSnapshot
    fun load(item: CastItem)
    fun loadNext(item: CastItem?)
    fun play()
    fun pause()
    fun stop()
    fun seek(positionMs: Long)
    fun next()
    fun previous()
    fun setVolume(percent: Int)
    fun setMuted(muted: Boolean)
}

/** Answers a controller's SOAP calls for the three services, driving [control]. */
class Renderer(private val control: CastControl) {
    class Reply(val status: Int, val body: String)

    fun handle(serviceType: String, action: SoapAction): Reply {
        val out = runCatching { answer(serviceType, action) }.getOrElse { e ->
            return if (e is UpnpError) Reply(500, Upnp.fault(e.code, e.message.orEmpty())) else Reply(500, Upnp.fault(501, "Action Failed"))
        } ?: return Reply(500, Upnp.fault(401, "Invalid Action"))
        return Reply(200, Upnp.response(serviceType, action.name, out))
    }

    private fun answer(service: String, action: SoapAction): List<Pair<String, String>>? {
        val a = action.args
        return when (service) {
            Upnp.AV_TRANSPORT -> when (action.name) {
                "SetAVTransportURI" -> { control.load(Upnp.item(a.need("CurrentURI"), a["CurrentURIMetaData"].orEmpty())); emptyList() }
                "SetNextAVTransportURI" -> {
                    val uri = a["NextURI"].orEmpty()
                    control.loadNext(if (uri.isBlank()) null else Upnp.item(uri, a["NextURIMetaData"].orEmpty())); emptyList()
                }
                "GetMediaInfo" -> control.snapshot().let { s ->
                    listOf(
                        "NrTracks" to (if (s.current == null) "0" else "1"), "MediaDuration" to Upnp.clock(s.durationMs),
                        "CurrentURI" to s.current?.uri.orEmpty(), "CurrentURIMetaData" to s.current?.metadata.orEmpty(),
                        "NextURI" to s.next?.uri.orEmpty(), "NextURIMetaData" to s.next?.metadata.orEmpty(),
                        "PlayMedium" to "NETWORK", "RecordMedium" to "NOT_IMPLEMENTED", "WriteStatus" to "NOT_IMPLEMENTED"
                    )
                }
                "GetTransportInfo" -> listOf(
                    "CurrentTransportState" to control.snapshot().transport.upnp, "CurrentTransportStatus" to "OK", "CurrentSpeed" to "1"
                )
                "GetPositionInfo" -> control.snapshot().let { s ->
                    listOf(
                        "Track" to (if (s.current == null) "0" else "1"), "TrackDuration" to Upnp.clock(s.durationMs),
                        "TrackMetaData" to s.current?.metadata.orEmpty(), "TrackURI" to s.current?.uri.orEmpty(),
                        "RelTime" to Upnp.clock(s.positionMs), "AbsTime" to Upnp.clock(s.positionMs),
                        "RelCount" to "2147483647", "AbsCount" to "2147483647"
                    )
                }
                "GetDeviceCapabilities" -> listOf("PlayMedia" to "NETWORK", "RecMedia" to "NOT_IMPLEMENTED", "RecQualityModes" to "NOT_IMPLEMENTED")
                "GetTransportSettings" -> listOf("PlayMode" to "NORMAL", "RecQualityMode" to "NOT_IMPLEMENTED")
                "GetCurrentTransportActions" -> listOf("Actions" to actions(control.snapshot()))
                "Play" -> { if (control.snapshot().current == null) throw UpnpError(701, "Transition not available"); control.play(); emptyList() }
                "Pause" -> { control.pause(); emptyList() }
                "Stop" -> { control.stop(); emptyList() }
                "Seek" -> {
                    val unit = a["Unit"].orEmpty()
                    if (unit != "REL_TIME" && unit != "ABS_TIME") throw UpnpError(710, "Seek mode not supported")
                    control.seek(Upnp.parseClock(a.need("Target")) ?: throw UpnpError(711, "Illegal seek target")); emptyList()
                }
                "Next" -> { control.next(); emptyList() }
                "Previous" -> { control.previous(); emptyList() }
                else -> null
            }
            Upnp.RENDERING_CONTROL -> when (action.name) {
                "ListPresets" -> listOf("CurrentPresetNameList" to "FactoryDefaults")
                "SelectPreset" -> emptyList()
                "GetVolume" -> listOf("CurrentVolume" to control.snapshot().volume.toString())
                "SetVolume" -> {
                    control.setVolume(a.need("DesiredVolume").toIntOrNull()?.coerceIn(0, 100) ?: throw UpnpError(402, "Invalid Args")); emptyList()
                }
                "GetMute" -> listOf("CurrentMute" to if (control.snapshot().muted) "1" else "0")
                "SetMute" -> { control.setMuted(a.need("DesiredMute").let { it == "1" || it.equals("true", ignoreCase = true) }); emptyList() }
                else -> null
            }
            Upnp.CONNECTION_MANAGER -> when (action.name) {
                "GetProtocolInfo" -> listOf("Source" to "", "Sink" to Upnp.SINK_PROTOCOLS)
                "GetCurrentConnectionIDs" -> listOf("ConnectionIDs" to "0")
                "GetCurrentConnectionInfo" -> listOf(
                    "RcsID" to "0", "AVTransportID" to "0", "ProtocolInfo" to "", "PeerConnectionManager" to "",
                    "PeerConnectionID" to "-1", "Direction" to "Input", "Status" to "OK"
                )
                else -> null
            }
            else -> null
        }
    }

    /** What an event for [serviceType] says: every evented value, so a new subscriber learns the lot. */
    fun state(serviceType: String): List<Pair<String, String>> {
        val s = control.snapshot()
        return when (serviceType) {
            Upnp.AV_TRANSPORT -> listOf(
                "LastChange" to Upnp.lastChange(
                    serviceType, listOf(
                        "TransportState" to s.transport.upnp, "TransportStatus" to "OK", "TransportPlaySpeed" to "1",
                        "NumberOfTracks" to (if (s.current == null) "0" else "1"), "CurrentTrack" to (if (s.current == null) "0" else "1"),
                        "AVTransportURI" to s.current?.uri.orEmpty(), "AVTransportURIMetaData" to s.current?.metadata.orEmpty(),
                        "CurrentTrackURI" to s.current?.uri.orEmpty(), "CurrentTrackMetaData" to s.current?.metadata.orEmpty(),
                        "NextAVTransportURI" to s.next?.uri.orEmpty(), "NextAVTransportURIMetaData" to s.next?.metadata.orEmpty(),
                        "CurrentTrackDuration" to Upnp.clock(s.durationMs), "CurrentMediaDuration" to Upnp.clock(s.durationMs),
                        "CurrentTransportActions" to actions(s), "PlaybackStorageMedium" to "NETWORK"
                    )
                )
            )
            Upnp.RENDERING_CONTROL -> listOf(
                "LastChange" to Upnp.lastChange(serviceType, listOf("Volume" to s.volume.toString(), "Mute" to if (s.muted) "1" else "0"))
            )
            else -> listOf("SourceProtocolInfo" to "", "SinkProtocolInfo" to Upnp.SINK_PROTOCOLS, "CurrentConnectionIDs" to "0")
        }
    }

    private fun actions(s: CastSnapshot): String = if (s.current == null) "" else "Play,Pause,Stop,Seek,Next,Previous"

    private fun Map<String, String>.need(name: String): String = this[name] ?: throw UpnpError(402, "Invalid Args")

    private class UpnpError(val code: Int, message: String) : Exception(message)
}
