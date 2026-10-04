package com.example.cinelocal.data.torrent

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.text.format.Formatter
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

object LocalNetworkUtils {

    fun getLocalIpAddress(context: Context): String {
        // Method 1: ConnectivityManager active network LinkProperties (Android 10+)
        try {
            val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (cm != null) {
                val activeNet = cm.activeNetwork
                if (activeNet != null) {
                    val caps = cm.getNetworkCapabilities(activeNet)
                    if (caps != null && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
                    ) {
                        val linkProps = cm.getLinkProperties(activeNet)
                        val ipv4 = linkProps?.linkAddresses?.mapNotNull { it.address as? Inet4Address }
                            ?.firstOrNull { !it.isLoopbackAddress && it.isSiteLocalAddress && !it.hostAddress.isNullOrBlank() }
                        if (ipv4?.hostAddress != null) {
                            return ipv4.hostAddress!!
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // Method 2: WifiManager IP address
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val wifiIp = wifiManager?.connectionInfo?.ipAddress ?: 0
            if (wifiIp != 0) {
                @Suppress("DEPRECATION")
                val formatted = Formatter.formatIpAddress(wifiIp)
                if (formatted != "0.0.0.0" && !formatted.startsWith("127.")) {
                    return formatted
                }
            }
        } catch (_: Exception) {}

        // Method 3: NetworkInterface enumeration sorted by wlan/eth priority
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            val sortedIntfs = interfaces.sortedWith(compareByDescending {
                val name = it.name.lowercase()
                when {
                    name.startsWith("wlan") -> 3
                    name.startsWith("eth") || name.startsWith("en") -> 2
                    name.startsWith("ap") || name.startsWith("p2p") -> 1
                    else -> 0
                }
            })

            for (intf in sortedIntfs) {
                if (intf.isLoopback || !intf.isUp) continue
                for (addr in Collections.list(intf.inetAddresses)) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val host = addr.hostAddress ?: ""
                        if (addr.isSiteLocalAddress && !host.startsWith("127.") && !host.startsWith("169.254.")) {
                            return host
                        }
                    }
                }
            }

            for (intf in sortedIntfs) {
                if (intf.isLoopback || !intf.isUp) continue
                for (addr in Collections.list(intf.inetAddresses)) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val host = addr.hostAddress ?: ""
                        if (!host.startsWith("127.") && !host.startsWith("169.254.")) {
                            return host
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        return "127.0.0.1"
    }

    fun getActiveInterfacesInfo(): List<String> {
        val list = mutableListOf<String>()
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                if (!intf.isUp) continue
                val addrs = Collections.list(intf.inetAddresses)
                    .filterIsInstance<Inet4Address>()
                    .mapNotNull { it.hostAddress }
                    .filter { it.isNotBlank() }
                if (addrs.isNotEmpty()) {
                    list.add("${intf.name}: ${addrs.joinToString(", ")}")
                }
            }
        } catch (_: Exception) {}
        return list
    }
}
