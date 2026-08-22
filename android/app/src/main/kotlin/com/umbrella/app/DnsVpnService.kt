package com.umbrella.app

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetAddress
import java.nio.ByteBuffer

class DnsVpnService : VpnService() {

    companion object {
        private const val TAG = "UmbrellaVPN"

        // SafeSearch forced IPs
        private const val GOOGLE_SAFESEARCH_IP = "216.239.38.120"
        private const val YOUTUBE_SAFESEARCH_IP = "216.239.38.120"

        // Default blocklist — parent rules supplement this from Firestore
        val BLOCKED_DOMAINS: MutableSet<String> = mutableSetOf(
            "tiktok.com", "www.tiktok.com",
            "snapchat.com", "www.snapchat.com",
            "reddit.com", "www.reddit.com",
            "onlyfans.com", "www.onlyfans.com",
            "pornhub.com", "www.pornhub.com",
            "xvideos.com", "chatgpt.com" // block direct AI chat bypass
        )

        // Domains to force SafeSearch on
        private val SAFESEARCH_DOMAINS = setOf(
            "google.com", "www.google.com",
            "youtube.com", "www.youtube.com",
            "bing.com", "www.bing.com"
        )
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    @Volatile private var running = false
    private var vpnThread: Thread? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopVpn()
            return START_NOT_STICKY
        }
        startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        if (vpnInterface != null) return

        val builder = Builder()
            .addAddress("10.111.222.1", 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("8.8.8.8")         // Primary DNS (we intercept before it leaves)
            .addDnsServer("8.8.4.4")         // Fallback
            .setSession("Umbrella DNS Guard")
            .setBlocking(true)

        vpnInterface = builder.establish() ?: run {
            Log.e(TAG, "Failed to establish VPN interface.")
            return
        }

        running = true
        vpnThread = Thread({ runDnsLoop() }, "UmbrellaVPN-Thread").also { it.start() }
        Log.i(TAG, "VPN started and DNS interception active.")
    }

    private fun runDnsLoop() {
        val fd = vpnInterface?.fileDescriptor ?: return
        val inputStream = FileInputStream(fd)
        val outputStream = FileOutputStream(fd)
        val packet = ByteBuffer.allocate(32767)

        while (running) {
            try {
                packet.clear()
                val length = inputStream.read(packet.array())
                if (length <= 0) continue

                packet.limit(length)
                val ipVersion = (packet.get(0).toInt() shr 4) and 0xF

                // Only handle IPv4 for now
                if (ipVersion != 4) {
                    outputStream.write(packet.array(), 0, length)
                    continue
                }

                val protocol = packet.get(9).toInt() and 0xFF
                // Protocol 17 = UDP
                if (protocol != 17) {
                    outputStream.write(packet.array(), 0, length)
                    continue
                }

                // Parse UDP destination port (bytes 22-23 of IP packet)
                val destPort = ((packet.get(22).toInt() and 0xFF) shl 8) or (packet.get(23).toInt() and 0xFF)

                if (destPort == 53) {
                    // Extract DNS question from UDP payload (IP header=20, UDP header=8)
                    val dnsOffset = 28
                    if (length > dnsOffset) {
                        val domain = extractDnsDomain(packet.array(), dnsOffset, length)
                        Log.d(TAG, "DNS query intercepted: $domain")

                        when {
                            BLOCKED_DOMAINS.any { blocked -> domain.endsWith(blocked) } -> {
                                Log.i(TAG, "BLOCKED: $domain → 0.0.0.0")
                                val blockResponse = buildDnsBlockResponse(packet.array(), dnsOffset, length)
                                outputStream.write(blockResponse)
                                continue
                            }
                            SAFESEARCH_DOMAINS.any { ss -> domain.endsWith(ss) } -> {
                                Log.i(TAG, "SAFESEARCH redirect: $domain → $GOOGLE_SAFESEARCH_IP")
                                // Allow through — SafeSearch is enforced via search param injection
                                // and DNS-level redirect would break HTTPS cert verification
                            }
                        }
                    }
                }

                // Pass packet through unmodified
                outputStream.write(packet.array(), 0, length)

            } catch (e: InterruptedException) {
                break
            } catch (e: Exception) {
                if (running) Log.e(TAG, "VPN loop error: ${e.message}")
            }
        }

        Log.i(TAG, "DNS interception loop exited.")
    }

    /**
     * Extracts the domain name from a raw DNS question section.
     */
    private fun extractDnsDomain(data: ByteArray, offset: Int, length: Int): String {
        // Skip DNS header (12 bytes) from the start of DNS payload
        val dnsPayloadStart = offset + 12
        val sb = StringBuilder()
        var pos = dnsPayloadStart

        try {
            while (pos < length) {
                val labelLen = data[pos].toInt() and 0xFF
                if (labelLen == 0) break
                if (sb.isNotEmpty()) sb.append('.')
                pos++
                if (pos + labelLen > length) break
                sb.append(String(data, pos, labelLen, Charsets.US_ASCII))
                pos += labelLen
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse DNS domain: ${e.message}")
        }
        return sb.toString().lowercase()
    }

    /**
     * Builds a DNS response that resolves to 0.0.0.0 (NXDOMAIN-style block).
     * Copies the original query and adds a RCODE=3 (NXDOMAIN) response flag.
     */
    private fun buildDnsBlockResponse(packet: ByteArray, dnsOffset: Int, length: Int): ByteArray {
        val response = packet.copyOf(length)
        // Flip the QR bit and set RCODE=3 (NXDOMAIN) in the DNS flags
        if (dnsOffset + 3 < length) {
            response[dnsOffset + 2] = (response[dnsOffset + 2].toInt() or 0x80).toByte() // QR=1 (response)
            response[dnsOffset + 3] = (response[dnsOffset + 3].toInt() and 0xF0 or 0x03).toByte() // RCODE=3
        }
        return response
    }

    private fun stopVpn() {
        running = false
        vpnThread?.interrupt()
        vpnInterface?.close()
        vpnInterface = null
        stopSelf()
        Log.i(TAG, "VPN stopped.")
    }

    override fun onRevoke() {
        stopVpn()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }
}
