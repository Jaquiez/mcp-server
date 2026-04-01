package net.portswigger.mcp.config

private const val MAX_HOST_LENGTH = 255

object OriginHostValidation {

    /**
     * Validates whether a hostname is valid for the origin allow-list.
     *
     * Valid formats include:
     * - Hostnames: host.docker.internal, mydevbox.local
     * - IP addresses: 192.168.1.100, 10.0.0.1
     * - IPv6 addresses: ::1, fe80::1
     *
     * Ports, wildcards, and schemes are not accepted.
     */
    fun isValidOriginHost(host: String): Boolean {
        if (host.isBlank() || host.length > MAX_HOST_LENGTH) return false

        if (host.contains("\t") || host.contains("\n") || host.contains("\r")) return false

        // Reject schemes
        if (host.contains("://")) return false

        // Reject ports (except in IPv6 bare addresses like ::1)
        if (host.contains(":") && !host.contains("::") && !isIpv6(host)) return false

        // Reject wildcards
        if (host.contains("*")) return false

        // Reject paths/queries
        if (host.contains("/") || host.contains("?") || host.contains("#")) return false

        // Reject spaces
        if (host.contains(" ")) return false

        return true
    }

    private fun isIpv6(host: String): Boolean {
        return host.contains(":")
    }
}
