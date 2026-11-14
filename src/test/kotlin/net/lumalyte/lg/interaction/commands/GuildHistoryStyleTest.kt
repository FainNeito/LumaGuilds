package net.lumalyte.lg.interaction.commands

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** REQ-238: /guild history uses rule lines, not box-drawing that never lines up in chat. */
class GuildHistoryStyleTest {
    private val root = Path.of(System.getProperty("user.dir"))

    private fun historyBlock(): String {
        val lang = Files.readString(root.resolve("src/main/resources/lang/en_US.yml"))
        val start = lang.indexOf("      history:\n        footer:")
        assertTrue(start >= 0, "history block")
        val end = lang.indexOf("      home:\n", start)
        return lang.substring(start, end)
    }

    @Test
    fun `history uses rule lines and no box drawing`() {
        val block = historyBlock()
        listOf('╔', '╗', '╚', '╝', '═').forEach { assertFalse(it in block, "found $it") }
        assertTrue("guild_history: '<dark_gray><strikethrough>" in block)
        assertTrue("footer: '<dark_gray><strikethrough>" in block)
    }

    @Test
    fun `history keeps every piece of information`() {
        val block = historyBlock()
        listOf("<display_name>", "<size>", "<index>", "<guild>", "<join_date>").forEach {
            assertTrue(it in block, "missing $it")
        }
        listOf("current:", "left:", "kicked:", "guild_disbanded:", "unknown:").forEach {
            assertTrue(it in block, "missing $it")
        }
    }
}
