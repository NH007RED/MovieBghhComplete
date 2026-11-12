package com.moviebox.app.net

import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class DnsProvider(
    val id: String,
    val name: String,
    val host: String,
    val dohUrl: String,
    val bootstrapIps: List<String> = emptyList()
)

object DnsProviders {
    const val SYSTEM = "system"
    const val CLOUDFLARE = "cloudflare"
    const val GOOGLE = "google"
    const val QUAD9 = "quad9"
    const val ADGUARD = "adguard"

    val all: List<DnsProvider> = listOf(
        DnsProvider(
            id = CLOUDFLARE,
            name = "Cloudflare 1.1.1.1 (DoH)",
            host = "1dot1dot1dot1.cloudflare-dns.com",
            dohUrl = "https://cloudflare-dns.com/dns-query",
            bootstrapIps = listOf("1.1.1.1", "1.0.0.1", "162.159.36.1", "162.159.46.1")
        ),
        DnsProvider(
            id = GOOGLE,
            name = "Google DNS (DoH)",
            host = "dns.google",
            dohUrl = "https://dns.google/dns-query",
            bootstrapIps = listOf("8.8.8.8", "8.8.4.4")
        ),
        DnsProvider(
            id = QUAD9,
            name = "Quad9 (DoH)",
            host = "dns.quad9.net",
            dohUrl = "https://dns.quad9.net/dns-query",
            bootstrapIps = listOf("9.9.9.9", "149.112.112.112")
        ),
        DnsProvider(
            id = ADGUARD,
            name = "AdGuard DNS (DoH)",
            host = "dns.adguard-dns.com",
            dohUrl = "https://dns.adguard-dns.com/dns-query",
            bootstrapIps = listOf("94.140.14.14", "94.140.15.15")
        ),
        DnsProvider(
            id = SYSTEM,
            name = "System DNS",
            host = "Uses phone Private DNS",
            dohUrl = "",
            bootstrapIps = emptyList()
        )
    )

    fun byId(id: String): DnsProvider = all.firstOrNull { it.id == id } ?: all.first()
}

// Smart DoH-first DNS resolver with hardcoded bootstrap IPv4 hosts:
// Crucial for Indian mobile carriers (Jio / Airtel 4G/5G) where System DNS sinkholes
// file-sharing domains (*.gofile.io) to a carrier IP that returns an empty 200 OK
// page ("<html><head></head><body></body></html>"), which causes ExoPlayer to fail with
// ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED.
// By resolving via Cloudflare (1.1.1.1) / Google (8.8.8.8) / Quad9 (9.9.9.9) DoH first
// using literal bootstrap IPs, ISP DNS poisoning is completely bypassed.
object SwitchableDns : Dns {
    private class Cached(val addresses: List<InetAddress>, val at: Long)

    @Volatile
    var activeProviderId: String = DnsProviders.CLOUDFLARE

    private val bootstrapClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }
    private val resolvers = ConcurrentHashMap<String, DnsOverHttps>()
    private val cache = ConcurrentHashMap<String, Cached>()

    private fun parseBootstrapHosts(ips: List<String>): List<InetAddress> =
        ips.mapNotNull { ip ->
            runCatching { InetAddress.getByName(ip) }.getOrNull()
        }

    private fun resolver(p: DnsProvider): DnsOverHttps =
        resolvers.getOrPut(p.id) {
            val builder = DnsOverHttps.Builder()
                .client(bootstrapClient)
                .url(p.dohUrl.toHttpUrl())
            val hosts = parseBootstrapHosts(p.bootstrapIps)
            if (hosts.isNotEmpty()) {
                builder.bootstrapDnsHosts(hosts)
            }
            builder.build()
        }

    private fun isUsableAddress(addr: InetAddress): Boolean {
        if (addr.isAnyLocalAddress || addr.isLoopbackAddress || addr.isLinkLocalAddress || addr.isSiteLocalAddress) {
            return false
        }
        val hostAddr = addr.hostAddress ?: return false
        if (hostAddr == "0.0.0.0" || hostAddr.startsWith("127.") || hostAddr == "::" || hostAddr == "::1") {
            return false
        }
        return true
    }

    fun lookupStrict(p: DnsProvider, host: String): List<InetAddress> {
        if (p.dohUrl.isEmpty()) return Dns.SYSTEM.lookup(host)
        val r = resolver(p).lookup(host).filter { isUsableAddress(it) }
        if (r.isEmpty()) {
            throw UnknownHostException("$host returned no usable addresses from ${p.name}")
        }
        return r
    }

    override fun lookup(hostname: String): List<InetAddress> {
        // If hostname is already a literal IP, resolve directly
        if (hostname.matches(Regex("""^\d{1,3}(\.\d{1,3}){3}$"""))) {
            return Dns.SYSTEM.lookup(hostname)
        }

        val now = System.currentTimeMillis()
        val key = "$activeProviderId|$hostname"
        cache[key]?.let { if (now - it.at < 180_000L) return it.addresses }

        // 1. Try active DoH provider first (default: Cloudflare 1.1.1.1 over HTTPS)
        val chosen = DnsProviders.byId(activeProviderId)
        if (chosen.dohUrl.isNotEmpty()) {
            try {
                val r = lookupStrict(chosen, hostname)
                cache[key] = Cached(r, now)
                return r
            } catch (_: Exception) {
            }
        }

        // 2. Try fallback DoH providers (Cloudflare -> Google -> Quad9 -> AdGuard)
        //    BEFORE touching System DNS so ISP DNS sinkholing can never return a fake IP!
        for (fallbackId in listOf(DnsProviders.CLOUDFLARE, DnsProviders.GOOGLE, DnsProviders.QUAD9, DnsProviders.ADGUARD)) {
            if (fallbackId == chosen.id) continue
            val fb = DnsProviders.byId(fallbackId)
            try {
                val r = lookupStrict(fb, hostname)
                if (r.isNotEmpty()) {
                    cache[key] = Cached(r, now)
                    return r
                }
            } catch (_: IOException) {
            } catch (_: RuntimeException) {
            }
        }

        // 3. Last resort: System DNS if all DoH endpoints are unreachable
        val sys = Dns.SYSTEM.lookup(hostname).filter { isUsableAddress(it) }
        if (sys.isNotEmpty()) {
            cache[key] = Cached(sys, now)
            return sys
        }
        return Dns.SYSTEM.lookup(hostname)
    }
}

object Net {
    // Shared OkHttpClient ported from VideoLinkFinder v2.2 with DoH-first SwitchableDns
    // and NO CookieJar so OkHttp's BridgeInterceptor never overwrites explicit Cookie headers.
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(SwitchableDns)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }
}
