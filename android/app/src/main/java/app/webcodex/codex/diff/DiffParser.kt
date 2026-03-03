package app.webcodex.codex.diff

/**
 * Parses unified diff format for display. Matches web DiffRenderer behavior.
 */
object DiffParser {
    sealed class Line {
        data class Meta(val raw: String) : Line()
        data class Hunk(val raw: String) : Line()
        data class Add(val raw: String) : Line()
        data class Del(val raw: String) : Line()
        data class Ctx(val raw: String) : Line()
    }

    fun parse(diff: String): List<Line> = diff.split("\n").map { line ->
        when {
            line.startsWith("+++") || line.startsWith("---") -> Line.Meta(line)
            line.startsWith("@@") -> Line.Hunk(line)
            line.startsWith("+") && !line.startsWith("+++") -> Line.Add(line)
            line.startsWith("-") && !line.startsWith("---") -> Line.Del(line)
            else -> Line.Ctx(line)
        }
    }

    fun countStats(lines: List<Line>): Pair<Int, Int> {
        var add = 0
        var del = 0
        for (l in lines) {
            when (l) {
                is Line.Add -> add++
                is Line.Del -> del++
                else -> {}
            }
        }
        return add to del
    }
}
