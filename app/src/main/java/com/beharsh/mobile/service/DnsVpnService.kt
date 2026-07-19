package com.beharsh.mobile.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer

class DnsVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var running = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopVpn(); return START_NOT_STICKY }
        startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        if (running) return
        running = true
        showNotification()
        vpnInterface = Builder()
            .setSession("BEHarsh-DNS")
            .addAddress("10.0.0.2", 32)
            .addDnsServer("1.1.1.3")
            .addRoute("0.0.0.0", 0)
            .setMtu(1500)
            .establish() ?: run { running = false; return }

        scope.launch { packetLoop() }
    }

    private suspend fun packetLoop() = withContext(Dispatchers.IO) {
        val pfd = vpnInterface ?: return@withContext
        val input  = FileInputStream(pfd.fileDescriptor)
        val output = FileOutputStream(pfd.fileDescriptor)
        val buf    = ByteArray(32767)

        while (running && isActive) {
            try {
                val len = input.read(buf)
                if (len < 28) continue
                val pkt = buf.copyOf(len)

                // Only handle IPv4 UDP port-53 packets
                if (!isDnsQuery(pkt)) continue

                // Extract fields needed to build the response packet
                val srcIp   = pkt.copyOfRange(12, 16)
                val dstIp   = pkt.copyOfRange(16, 20)
                val srcPort = ((pkt[20].toInt() and 0xFF) shl 8) or (pkt[21].toInt() and 0xFF)
                val udpLen  = ((pkt[24].toInt() and 0xFF) shl 8) or (pkt[25].toInt() and 0xFF)
                val dnsPayloadLen = udpLen - 8
                if (dnsPayloadLen <= 0 || 28 + dnsPayloadLen > len) continue
                val dnsPayload = pkt.copyOfRange(28, 28 + dnsPayloadLen)

                // Forward to Cloudflare Family asynchronously so the read loop never blocks
                launch {
                    try {
                        val sock = DatagramSocket()
                        protect(sock) // exempt from VPN so it goes out the real interface
                        val cf = InetAddress.getByName("1.1.1.3")
                        sock.soTimeout = 3000
                        sock.send(DatagramPacket(dnsPayload, dnsPayload.size, cf, 53))

                        val respBuf = ByteArray(4096)
                        val respPkt = DatagramPacket(respBuf, respBuf.size)
                        sock.receive(respPkt)
                        sock.close()

                        val resp = respPkt.data.copyOf(respPkt.length)
                        val reply = buildIpUdpPacket(
                            srcIp   = dstIp,   // swap: response comes FROM the DNS server IP
                            dstIp   = srcIp,   // back TO the original requester
                            srcPort = 53,
                            dstPort = srcPort,
                            payload = resp
                        )
                        synchronized(output) {
                            output.write(reply)
                        }
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {
                if (!running) break
            }
        }
        input.close()
        output.close()
    }

    /**
     * Builds a minimal IPv4/UDP packet with correct IP checksum.
     * UDP checksum is left as 0 (valid per RFC 768 for IPv4).
     */
    private fun buildIpUdpPacket(
        srcIp: ByteArray, dstIp: ByteArray,
        srcPort: Int, dstPort: Int,
        payload: ByteArray
    ): ByteArray {
        val udpLen   = 8 + payload.size
        val totalLen = 20 + udpLen
        val bb = ByteBuffer.allocate(totalLen)

        // IPv4 header
        bb.put(0x45.toByte())                        // Version=4, IHL=5
        bb.put(0x00.toByte())                        // DSCP/ECN
        bb.putShort(totalLen.toShort())              // Total length
        bb.putShort(0.toShort())                     // Identification
        bb.putShort(0x4000.toShort())                // Flags: Don't Fragment
        bb.put(64.toByte())                          // TTL
        bb.put(17.toByte())                          // Protocol: UDP
        bb.putShort(0.toShort())                     // Checksum placeholder
        bb.put(srcIp)
        bb.put(dstIp)

        // Fill IP checksum at offset 10
        val ipChecksum = ipChecksum(bb.array(), 0, 20)
        bb.array()[10] = (ipChecksum shr 8).toByte()
        bb.array()[11] = (ipChecksum and 0xFF).toByte()

        // UDP header
        bb.putShort(srcPort.toShort())
        bb.putShort(dstPort.toShort())
        bb.putShort(udpLen.toShort())
        bb.putShort(0.toShort())                     // UDP checksum = 0 (optional for IPv4)

        // DNS payload
        bb.put(payload)
        return bb.array()
    }

    private fun ipChecksum(buf: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var i = offset
        while (i < offset + length - 1) {
            sum += ((buf[i].toInt() and 0xFF) shl 8) or (buf[i + 1].toInt() and 0xFF)
            i += 2
        }
        while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        return sum.inv() and 0xFFFF
    }

    private fun isDnsQuery(pkt: ByteArray): Boolean {
        if (pkt.size < 28) return false
        if (pkt[0].toInt() and 0xFF != 0x45) return false  // IPv4, IHL=5
        if (pkt[9].toInt() and 0xFF != 17) return false    // UDP
        val dstPort = ((pkt[22].toInt() and 0xFF) shl 8) or (pkt[23].toInt() and 0xFF)
        return dstPort == 53
    }

    private fun stopVpn() {
        running = false
        scope.cancel()
        vpnInterface?.close()
        vpnInterface = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION") stopForeground(true)
        }
        stopSelf()
    }

    private fun showNotification() {
        val ch = "dns_vpn"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(ch, "DNS Filter", NotificationManager.IMPORTANCE_LOW)
            )
        }
        startForeground(
            2001,
            NotificationCompat.Builder(this, ch)
                .setContentTitle("BE Harsh — DNS Filter Active")
                .setContentText("Routing DNS through Cloudflare Family (1.1.1.3)")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .build()
        )
    }

    override fun onDestroy() { stopVpn(); super.onDestroy() }
    override fun onBind(intent: Intent?) = super.onBind(intent)

    companion object {
        const val ACTION_STOP = "STOP_VPN"
        fun start(ctx: Context) {
            val i = Intent(ctx, DnsVpnService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
            else ctx.startService(i)
        }
        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, DnsVpnService::class.java).setAction(ACTION_STOP))
        }
    }
}
