package xyz.mastriel.cutapi.item.systems

import kotlin.test.Test
import kotlin.test.assertEquals

public class ItemOriginSystemTest {
    @Test
    public fun `small caps converts lowercase and uppercase latin letters`() {
        val expected = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘꞯʀꜱᴛᴜᴠᴡxʏᴢ"

        assertEquals(expected, "abcdefghijklmnopqrstuvwxyz".toSmallCaps())
        assertEquals(expected, "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toSmallCaps())
        assertEquals("ʜᴇʟʟᴏ", "Hello".toSmallCaps())
    }

    @Test
    public fun `small caps preserves non-ascii letters and non-letter characters`() {
        assertEquals(
            "ᴄᴜᴛᴀᴘɪ 1.21! ᴄᴀꜰé 😀",
            "CuTAPI 1.21! Café 😀".toSmallCaps(),
        )
    }
}
