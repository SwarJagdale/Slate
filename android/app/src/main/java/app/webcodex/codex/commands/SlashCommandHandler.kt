package app.webcodex.codex.commands

/**
 * Parses and dispatches slash commands matching web client behavior.
 * Commands: /new, /threads, /resume, /model, /interrupt, /review, /archive, /unarchive,
 * /fork, /rollback, /compact, /diff, /plan, /skills, /mcp, /apps, /experimental, /status,
 * /clearterminals, /run, /rename, /settings, /approval, /permissions, /sandbox, /logout, /quit, /help
 */
object SlashCommandHandler {
    fun parse(text: String): ParsedCommand? {
        val trimmed = text.trim()
        if (!trimmed.startsWith("/")) return null
        val parts = trimmed.drop(1).split(Regex("\\s+")).filter { it.isNotEmpty() }
        val cmd = parts.getOrNull(0)?.lowercase() ?: return null
        val args = parts.drop(1)
        return ParsedCommand(cmd, args)
    }

    fun isSlashCommand(text: String): Boolean = text.trim().startsWith("/")
}

data class ParsedCommand(val command: String, val args: List<String>)
