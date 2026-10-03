package com.example.cinelocal.data.smb

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

object NetworkDiscovery {

    private const val TAG = "NetworkDiscovery"
    private const val SMB_PORT = 445
    private const val TIMEOUT_MS = 250
    private const val MAX_CONCURRENCY = 30

    suspend fun discoverLocalPcs(
        context: Context,
        onProgress: ((scanned: Int, total: Int, found: DiscoveredPc?) -> Unit)? = null
    ): List<DiscoveredPc> = withContext(Dispatchers.IO) {
        val localIp = getDeviceIpAddress(context)
        if (localIp == null || localIp == "127.0.0.1") {
            Log.w(TAG, "IP local não encontrado ou sem conexão Wi-Fi ativa")
            return@withContext emptyList()
        }

        val prefix = localIp.substringBeforeLast('.') + "."
        val targetIps = (1..254).map { "$prefix$it" }
        val foundPcs = mutableListOf<DiscoveredPc>()
        val semaphore = Semaphore(MAX_CONCURRENCY)
        var scannedCount = 0

        coroutineScope {
            val tasks = targetIps.map { ip ->
                async {
                    val isUp = semaphore.withPermit {
                        checkSmbPort(ip, SMB_PORT, TIMEOUT_MS)
                    }
                    var discovered: DiscoveredPc? = null
                    if (isUp) {
                        val hostName = resolveHostName(ip)
                        discovered = DiscoveredPc(ip = ip, hostName = hostName, isReachable = true)
                        synchronized(foundPcs) {
                            foundPcs.add(discovered)
                        }
                    }
                    synchronized(this@NetworkDiscovery) {
                        scannedCount++
                        onProgress?.invoke(scannedCount, targetIps.size, discovered)
                    }
                }
            }
            tasks.awaitAll()
        }

        foundPcs.sortedBy { it.ip }
    }

    private fun checkSmbPort(ip: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), timeoutMs)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun resolveHostName(ip: String): String {
        return try {
            val inet = InetAddress.getByName(ip)
            val host = inet.hostName
            if (host.isNotBlank() && host != ip) host else "PC ($ip)"
        } catch (_: Exception) {
            "PC ($ip)"
        }
    }

    fun getDeviceIpAddress(context: Context): String? {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val wifiIp = wifiManager?.connectionInfo?.ipAddress
            if (wifiIp != null && wifiIp != 0) {
                return String.format(
                    "%d.%d.%d.%d",
                    wifiIp and 0xff,
                    wifiIp shr 8 and 0xff,
                    wifiIp shr 16 and 0xff,
                    wifiIp shr 24 and 0xff
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro lendo IP via WifiManager", e)
        }

        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr.hostAddress?.contains(':') == false) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro lendo IP via NetworkInterfaces", e)
        }

        return null
    }
}
