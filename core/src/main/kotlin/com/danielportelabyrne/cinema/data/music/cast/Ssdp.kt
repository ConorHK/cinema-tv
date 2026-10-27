package com.danielportelabyrne.cinema.data.music.cast

import com.danielportelabyrne.cinema.platform.Log
import java.net.DatagramPacket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.InterfaceAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.SocketException

/**
 * SSDP, how controllers find the renderer: answers searches on 239.255.255.250:1900 and announces itself when it
 * starts, every few minutes after, and with a goodbye when it stops.
 */
class Ssdp(private val uuid: String, private val port: Int) {
    @Volatile private var socket: MulticastSocket? = null
    @Volatile private var running = false
    private var threads = emptyList<Thread>()

    fun start() {
        running = true
        threads = listOf(
            Thread({ listen() }, "ssdp-listen"),
            Thread({
                while (running) {
                    announce(alive = true)
                    try { Thread.sleep(ANNOUNCE_MS) } catch (_: InterruptedException) { return@Thread }
                }
            }, "ssdp-announce"),
        ).onEach { it.isDaemon = true; it.start() }
    }

    /** Says goodbye on the network; call off the main thread. */
    fun stop() {
        running = false
        threads.forEach { it.interrupt() }
        socket?.close()
        announce(alive = false)
    }

    private fun listen() {
        while (running) {
            try {
                MulticastSocket(null).use { s ->
                    s.reuseAddress = true
                    s.bind(InetSocketAddress(PORT))
                    val group = InetSocketAddress(InetAddress.getByName(GROUP), PORT)
                    val joined = interfaces().count { (iface, _) -> runCatching { s.joinGroup(group, iface) }.isSuccess }
                    if (joined == 0) throw SocketException("no network")
                    Log.i(TAG, "listening for searches on $joined interface(s)")
                    socket = s
                    val buf = ByteArray(2048)
                    while (running) {
                        val p = DatagramPacket(buf, buf.size)
                        s.receive(p)
                        val st = searchTarget(String(p.data, 0, p.length, Charsets.ISO_8859_1)) ?: continue
                        val local = addressFor(p.address) ?: continue
                        // From port 1900, so stateful firewalls let a direct search's answer back in.
                        for ((nt, usn) in targets(st, uuid)) {
                            val reply = response(nt, usn, location(local)).toByteArray(Charsets.ISO_8859_1)
                            s.send(DatagramPacket(reply, reply.size, p.socketAddress))
                        }
                    }
                }
            } catch (e: Exception) {
                if (!running) return
                Log.w(TAG, "listen failed, retrying", e)
                try { Thread.sleep(10_000) } catch (_: InterruptedException) { return }
            }
        }
    }

    private fun announce(alive: Boolean) {
        val group = InetAddress.getByName(GROUP)
        for ((iface, address) in interfaces()) {
            runCatching {
                MulticastSocket().use { s ->
                    s.networkInterface = iface
                    // Twice, as UDP may drop one.
                    repeat(2) {
                        for ((nt, usn) in targets(ALL, uuid)) {
                            val msg = notify(nt, usn, location(address.address), alive).toByteArray(Charsets.ISO_8859_1)
                            s.send(DatagramPacket(msg, msg.size, group, PORT))
                        }
                    }
                }
            }.onFailure { Log.w(TAG, "announce on ${iface.name} failed", it) }
        }
    }

    private fun location(address: InetAddress) = "http://${address.hostAddress}:$port/description.xml"

    /** This device's address on the network [peer] is on. */
    private fun addressFor(peer: InetAddress): InetAddress? {
        val all = interfaces()
        return all.firstOrNull { (_, a) -> sameSubnet(a, peer) }?.second?.address ?: all.firstOrNull()?.second?.address
    }

    companion object {
        const val GROUP = "239.255.255.250"
        const val PORT = 1900
        const val ALL = "ssdp:all"
        const val SERVER = "Linux UPnP/1.0 Cinema/1.0"
        private const val TAG = "CinemaCast"
        private const val ANNOUNCE_MS = 10 * 60_000L

        /** IPv4 addresses on interfaces that are up and can multicast. */
        fun interfaces(): List<Pair<NetworkInterface, InterfaceAddress>> = runCatching {
            NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback && it.supportsMulticast() }
                .flatMap { iface -> iface.interfaceAddresses.filter { it.address is Inet4Address }.map { iface to it } }
        }.getOrDefault(emptyList())

        private fun sameSubnet(a: InterfaceAddress, peer: InetAddress): Boolean {
            val bits = a.networkPrefixLength.toInt()
            if (peer !is Inet4Address || bits !in 1..32) return false
            val mask = -1 shl (32 - bits)
            return (toInt(a.address) and mask) == (toInt(peer) and mask)
        }

        private fun toInt(a: InetAddress) = a.address.fold(0) { acc, b -> (acc shl 8) or (b.toInt() and 0xFF) }

        /** The search target in an M-SEARCH discovery, or null when [packet] is anything else. */
        fun searchTarget(packet: String): String? {
            val lines = packet.split("\r\n", "\n")
            if (!lines.first().startsWith("M-SEARCH", ignoreCase = true)) return null
            fun header(name: String) = lines.firstOrNull { it.startsWith("$name:", ignoreCase = true) }?.substringAfter(':')?.trim()
            if (header("MAN")?.contains("ssdp:discover") != true) return null
            return header("ST")
        }

        /** The NT and USN pairs that answer a search for [st]; empty when it is not for this device. */
        fun targets(st: String, uuid: String): List<Pair<String, String>> {
            val udn = "uuid:$uuid"
            val all = listOf("upnp:rootdevice" to "$udn::upnp:rootdevice", udn to udn, Upnp.DEVICE_TYPE to "$udn::${Upnp.DEVICE_TYPE}") +
                Upnp.SERVICES.map { it to "$udn::$it" }
            return if (st == ALL) all else all.filter { it.first == st }
        }

        fun response(st: String, usn: String, location: String): String =
            "HTTP/1.1 200 OK\r\nCACHE-CONTROL: max-age=1800\r\nEXT:\r\nLOCATION: $location\r\nSERVER: $SERVER\r\n" +
                "ST: $st\r\nUSN: $usn\r\n\r\n"

        fun notify(nt: String, usn: String, location: String, alive: Boolean): String =
            "NOTIFY * HTTP/1.1\r\nHOST: $GROUP:$PORT\r\nCACHE-CONTROL: max-age=1800\r\nLOCATION: $location\r\n" +
                "NT: $nt\r\nNTS: ${if (alive) "ssdp:alive" else "ssdp:byebye"}\r\nSERVER: $SERVER\r\nUSN: $usn\r\n\r\n"
    }
}
