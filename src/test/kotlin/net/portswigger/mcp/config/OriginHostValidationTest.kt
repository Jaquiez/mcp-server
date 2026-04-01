package net.portswigger.mcp.config

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class OriginHostValidationTest {

    @Test
    fun `should accept valid hostnames`() {
        assertTrue(OriginHostValidation.isValidOriginHost("host.docker.internal"))
        assertTrue(OriginHostValidation.isValidOriginHost("mydevbox.local"))
        assertTrue(OriginHostValidation.isValidOriginHost("example.com"))
        assertTrue(OriginHostValidation.isValidOriginHost("localhost"))
    }

    @Test
    fun `should accept valid IP addresses`() {
        assertTrue(OriginHostValidation.isValidOriginHost("192.168.1.100"))
        assertTrue(OriginHostValidation.isValidOriginHost("10.0.0.1"))
        assertTrue(OriginHostValidation.isValidOriginHost("127.0.0.1"))
    }

    @Test
    fun `should accept IPv6 addresses`() {
        assertTrue(OriginHostValidation.isValidOriginHost("::1"))
        assertTrue(OriginHostValidation.isValidOriginHost("fe80::1"))
    }

    @Test
    fun `should reject blank input`() {
        assertFalse(OriginHostValidation.isValidOriginHost(""))
        assertFalse(OriginHostValidation.isValidOriginHost("   "))
    }

    @Test
    fun `should reject schemes`() {
        assertFalse(OriginHostValidation.isValidOriginHost("http://example.com"))
        assertFalse(OriginHostValidation.isValidOriginHost("https://example.com"))
    }

    @Test
    fun `should reject wildcards`() {
        assertFalse(OriginHostValidation.isValidOriginHost("*.example.com"))
        assertFalse(OriginHostValidation.isValidOriginHost("*"))
    }

    @Test
    fun `should reject paths and queries`() {
        assertFalse(OriginHostValidation.isValidOriginHost("example.com/path"))
        assertFalse(OriginHostValidation.isValidOriginHost("example.com?query"))
        assertFalse(OriginHostValidation.isValidOriginHost("example.com#fragment"))
    }

    @Test
    fun `should reject control characters`() {
        assertFalse(OriginHostValidation.isValidOriginHost("example\t.com"))
        assertFalse(OriginHostValidation.isValidOriginHost("example\n.com"))
        assertFalse(OriginHostValidation.isValidOriginHost("example\r.com"))
    }

    @Test
    fun `should reject hosts with ports`() {
        assertFalse(OriginHostValidation.isValidOriginHost("example.com:8080"))
        assertFalse(OriginHostValidation.isValidOriginHost("localhost:3000"))
    }

    @Test
    fun `should reject overly long input`() {
        val longHost = "a".repeat(256)
        assertFalse(OriginHostValidation.isValidOriginHost(longHost))
    }

    @Test
    fun `should accept max length input`() {
        val maxHost = "a".repeat(255)
        assertTrue(OriginHostValidation.isValidOriginHost(maxHost))
    }
}
