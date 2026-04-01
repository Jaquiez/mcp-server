package net.portswigger.mcp.config

import burp.api.montoya.logging.Logging
import burp.api.montoya.persistence.PersistedObject
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class McpConfigTest {

    private lateinit var persistedObject: PersistedObject
    private lateinit var config: McpConfig
    private lateinit var mockLogging: Logging

    @BeforeEach
    fun setup() {
        val storage = mutableMapOf<String, Any>()

        persistedObject = mockk<PersistedObject>().apply {
            every { getBoolean(any()) } answers {
                val key = firstArg<String>()
                storage[key] as? Boolean ?: when (key) {
                    "enabled" -> true
                    "requireHttpRequestApproval" -> true
                    else -> false
                }
            }
            every { getString(any()) } answers { storage[firstArg()] as? String ?: "" }
            every { getInteger(any()) } answers { storage[firstArg()] as? Int ?: 0 }
            every { setBoolean(any(), any()) } answers {
                storage[firstArg()] = secondArg<Boolean>()
            }
            every { setString(any(), any()) } answers {
                storage[firstArg()] = secondArg<String>()
            }
            every { setInteger(any(), any()) } answers {
                storage[firstArg()] = secondArg<Int>()
            }
        }

        mockLogging = mockk<Logging>().apply {
            every { logToError(any<String>()) } returns Unit
        }

        config = McpConfig(persistedObject, mockLogging)
    }

    @Test
    fun `addAutoApproveTarget should add new target`() {
        val result = config.addAutoApproveTarget("example.com")

        assertTrue(result)
        assertEquals("example.com", config.autoApproveTargets)
        verify { persistedObject.setString("_autoApproveTargets", "example.com") }
    }

    @Test
    fun `addAutoApproveTarget should not add duplicate target`() {
        config.addAutoApproveTarget("example.com")
        val result = config.addAutoApproveTarget("example.com")

        assertFalse(result)
        assertEquals("example.com", config.autoApproveTargets)
    }

    @Test
    fun `addAutoApproveTarget should trim whitespace`() {
        val result = config.addAutoApproveTarget("  example.com  ")

        assertTrue(result)
        assertEquals("example.com", config.autoApproveTargets)
    }

    @Test
    fun `addAutoApproveTarget should not add empty target`() {
        val result = config.addAutoApproveTarget("   ")

        assertFalse(result)
        assertEquals("", config.autoApproveTargets)
    }

    @Test
    fun `addAutoApproveTarget should handle multiple targets`() {
        config.addAutoApproveTarget("example.com")
        config.addAutoApproveTarget("test.org")

        assertEquals("example.com,test.org", config.autoApproveTargets)
        assertEquals(listOf("example.com", "test.org"), config.getAutoApproveTargetsList())
    }

    @Test
    fun `removeAutoApproveTarget should remove existing target`() {
        config.addAutoApproveTarget("example.com")
        config.addAutoApproveTarget("test.org")

        val result = config.removeAutoApproveTarget("example.com")

        assertTrue(result)
        assertEquals("test.org", config.autoApproveTargets)
        assertEquals(listOf("test.org"), config.getAutoApproveTargetsList())
    }

    @Test
    fun `removeAutoApproveTarget should return false for non-existing target`() {
        config.addAutoApproveTarget("example.com")

        val result = config.removeAutoApproveTarget("notfound.com")

        assertFalse(result)
        assertEquals("example.com", config.autoApproveTargets)
    }

    @Test
    fun `clearAutoApproveTargets should remove all targets`() {
        config.addAutoApproveTarget("example.com")
        config.addAutoApproveTarget("test.org")

        config.clearAutoApproveTargets()

        assertEquals("", config.autoApproveTargets)
        assertEquals(emptyList<String>(), config.getAutoApproveTargetsList())
    }

    @Test
    fun `getAutoApproveTargetsList should handle empty config`() {
        assertEquals(emptyList<String>(), config.getAutoApproveTargetsList())
    }

    @Test
    fun `getAutoApproveTargetsList should parse comma-separated values`() {
        val storage = mutableMapOf<String, Any>("_autoApproveTargets" to "example.com,test.org,*.api.com")
        persistedObject = mockk<PersistedObject>().apply {
            every { getBoolean(any()) } answers { storage[firstArg()] as? Boolean ?: false }
            every { getString(any()) } answers { storage[firstArg()] as? String ?: "" }
            every { getInteger(any()) } answers { storage[firstArg()] as? Int ?: 0 }
            every { setBoolean(any(), any()) } answers {
                storage[firstArg()] = secondArg<Boolean>()
            }
            every { setString(any(), any()) } answers {
                storage[firstArg()] = secondArg<String>()
            }
            every { setInteger(any(), any()) } answers {
                storage[firstArg()] = secondArg<Int>()
            }
        }
        config = McpConfig(persistedObject, mockLogging)

        assertEquals(
            listOf("example.com", "test.org", "*.api.com"), config.getAutoApproveTargetsList()
        )
    }

    @Test
    fun `getAutoApproveTargetsList should handle malformed input`() {
        val storage = mutableMapOf<String, Any>("_autoApproveTargets" to "example.com,,  ,test.org")
        persistedObject = mockk<PersistedObject>().apply {
            every { getBoolean(any()) } answers { storage[firstArg()] as? Boolean ?: false }
            every { getString(any()) } answers { storage[firstArg()] as? String ?: "" }
            every { getInteger(any()) } answers { storage[firstArg()] as? Int ?: 0 }
            every { setBoolean(any(), any()) } answers {
                storage[firstArg()] = secondArg<Boolean>()
            }
            every { setString(any(), any()) } answers {
                storage[firstArg()] = secondArg<String>()
            }
            every { setInteger(any(), any()) } answers {
                storage[firstArg()] = secondArg<Int>()
            }
        }
        config = McpConfig(persistedObject, mockLogging)

        assertEquals(
            listOf("example.com", "test.org"), config.getAutoApproveTargetsList()
        )
    }

    @Test
    fun `targets change listener should be notified`() {
        var notificationCount = 0
        val listener = {
            notificationCount++
            Unit
        }

        config.addTargetsChangeListener(listener)
        config.addAutoApproveTarget("example.com")

        assertEquals(1, notificationCount)
    }

    @Test
    fun `targets change listener should handle exceptions`() {
        val badListener = { throw RuntimeException("Test exception") }
        val goodListener = { /* do nothing */ }

        config.addTargetsChangeListener(badListener)
        config.addTargetsChangeListener(goodListener)

        assertDoesNotThrow {
            config.addAutoApproveTarget("example.com")
        }
    }

    @Test
    fun `autoApproveTargets setter should only notify on actual changes`() {
        var notificationCount = 0
        val listener = {
            notificationCount++
            Unit
        }

        config.addTargetsChangeListener(listener)

        config.autoApproveTargets = "example.com"
        assertEquals(1, notificationCount)

        config.autoApproveTargets = "example.com"
        assertEquals(1, notificationCount)

        config.autoApproveTargets = "test.org"
        assertEquals(2, notificationCount)
    }

    @Test
    fun `configEditingTooling should persist correctly`() {
        assertFalse(config.configEditingTooling)

        config.configEditingTooling = true
        assertTrue(config.configEditingTooling)
        verify { persistedObject.setBoolean("configEditingTooling", true) }

        config.configEditingTooling = false
        assertFalse(config.configEditingTooling)
        verify { persistedObject.setBoolean("configEditingTooling", false) }
    }

    @Test
    fun `requireHttpRequestApproval should persist correctly`() {
        assertTrue(config.requireHttpRequestApproval)

        config.requireHttpRequestApproval = false
        assertFalse(config.requireHttpRequestApproval)
        verify { persistedObject.setBoolean("requireHttpRequestApproval", false) }

        config.requireHttpRequestApproval = true
        assertTrue(config.requireHttpRequestApproval)
        verify { persistedObject.setBoolean("requireHttpRequestApproval", true) }
    }

    @Test
    fun `addAllowedOriginHost should add new host`() {
        val result = config.addAllowedOriginHost("host.docker.internal")

        assertTrue(result)
        assertEquals("host.docker.internal", config.allowedOriginHosts)
        verify { persistedObject.setString("_allowedOriginHosts", "host.docker.internal") }
    }

    @Test
    fun `addAllowedOriginHost should not add duplicate host`() {
        config.addAllowedOriginHost("host.docker.internal")
        val result = config.addAllowedOriginHost("host.docker.internal")

        assertFalse(result)
        assertEquals("host.docker.internal", config.allowedOriginHosts)
    }

    @Test
    fun `addAllowedOriginHost should trim whitespace`() {
        val result = config.addAllowedOriginHost("  host.docker.internal  ")

        assertTrue(result)
        assertEquals("host.docker.internal", config.allowedOriginHosts)
    }

    @Test
    fun `addAllowedOriginHost should not add empty host`() {
        val result = config.addAllowedOriginHost("   ")

        assertFalse(result)
        assertEquals("", config.allowedOriginHosts)
    }

    @Test
    fun `addAllowedOriginHost should handle multiple hosts`() {
        config.addAllowedOriginHost("host.docker.internal")
        config.addAllowedOriginHost("192.168.1.100")

        assertEquals("host.docker.internal,192.168.1.100", config.allowedOriginHosts)
        assertEquals(listOf("host.docker.internal", "192.168.1.100"), config.getAllowedOriginHostsList())
    }

    @Test
    fun `removeAllowedOriginHost should remove existing host`() {
        config.addAllowedOriginHost("host.docker.internal")
        config.addAllowedOriginHost("192.168.1.100")

        val result = config.removeAllowedOriginHost("host.docker.internal")

        assertTrue(result)
        assertEquals("192.168.1.100", config.allowedOriginHosts)
    }

    @Test
    fun `removeAllowedOriginHost should return false for non-existing host`() {
        config.addAllowedOriginHost("host.docker.internal")

        val result = config.removeAllowedOriginHost("notfound.com")

        assertFalse(result)
        assertEquals("host.docker.internal", config.allowedOriginHosts)
    }

    @Test
    fun `clearAllowedOriginHosts should remove all hosts`() {
        config.addAllowedOriginHost("host.docker.internal")
        config.addAllowedOriginHost("192.168.1.100")

        config.clearAllowedOriginHosts()

        assertEquals("", config.allowedOriginHosts)
        assertEquals(emptyList<String>(), config.getAllowedOriginHostsList())
    }

    @Test
    fun `getAllowedOriginHostsList should handle empty config`() {
        assertEquals(emptyList<String>(), config.getAllowedOriginHostsList())
    }

    @Test
    fun `allowed origins change listener should be notified`() {
        var notificationCount = 0
        val listener = {
            notificationCount++
            Unit
        }

        config.addAllowedOriginsChangeListener(listener)
        config.addAllowedOriginHost("host.docker.internal")

        assertEquals(1, notificationCount)
    }

    @Test
    fun `allowedOriginHosts setter should only notify on actual changes`() {
        var notificationCount = 0
        val listener = {
            notificationCount++
            Unit
        }

        config.addAllowedOriginsChangeListener(listener)

        config.allowedOriginHosts = "host.docker.internal"
        assertEquals(1, notificationCount)

        config.allowedOriginHosts = "host.docker.internal"
        assertEquals(1, notificationCount)

        config.allowedOriginHosts = "192.168.1.100"
        assertEquals(2, notificationCount)
    }
}