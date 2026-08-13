package xyz.mastriel.cutapi.resources.uploader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ResourcePackAddressTest {

    @Test
    fun `extracts the host used by the minecraft connection`() {
        assertEquals("localhost", resourcePackHostFromHandshake("localhost:25565"))
        assertEquals("192.168.1.172", resourcePackHostFromHandshake("192.168.1.172:25565"))
        assertEquals("play.example.com", resourcePackHostFromHandshake("play.example.com"))
        assertEquals("::1", resourcePackHostFromHandshake("[::1]:25565"))
    }

    @Test
    fun `ignores proxy forwarding data and rejects unsafe hosts`() {
        assertEquals("play.example.com", resourcePackHostFromHandshake("play.example.com\u0000forwarding-data"))
        assertNull(resourcePackHostFromHandshake("example.com/path"))
        assertNull(resourcePackHostFromHandshake(""))
        assertNull(resourcePackHostFromHandshake(null))
    }

    @Test
    fun `builds valid ipv4 hostname and ipv6 urls`() {
        assertEquals("http://localhost:32120/", resourcePackHttpUrl("localhost", 32120))
        assertEquals("http://192.168.1.172:32120/", resourcePackHttpUrl("192.168.1.172", 32120))
        assertEquals("http://[::1]:32120/", resourcePackHttpUrl("::1", 32120))
    }
}
