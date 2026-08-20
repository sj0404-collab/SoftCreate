package com.mobileforge.engine

import org.json.JSONObject

data class FileChange(
    val id: Long,
    val path: String,
    val at: Long,
    val author: String,
    val before: String,
    val after: String,
)

data class DiffLine(val kind: Char, val text: String)

object ChangeLog {
    fun diff(before: String, after: String): List<DiffLine> {
        val a = before.split('\n')
        val b = after.split('\n')
        if (a == b) return emptyList()
        if (a.size * b.size > 90_000) {
            return a.map { DiffLine('-', it) } + b.map { DiffLine('+', it) }
        }
        val n = a.size
        val m = b.size
        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) {
            for (j in m - 1 downTo 0) {
                dp[i][j] = if (a[i] == b[j]) dp[i + 1][j + 1] + 1 else maxOf(dp[i + 1][j], dp[i][j + 1])
            }
        }
        val out = ArrayList<DiffLine>(n + m)
        var i = 0
        var j = 0
        while (i < n && j < m) {
            when {
                a[i] == b[j] -> { out += DiffLine(' ', a[i]); i++; j++ }
                dp[i + 1][j] >= dp[i][j + 1] -> { out += DiffLine('-', a[i]); i++ }
                else -> { out += DiffLine('+', b[j]); j++ }
            }
        }
        while (i < n) out += DiffLine('-', a[i++])
        while (j < m) out += DiffLine('+', b[j++])
        return out
    }

    fun summary(change: FileChange): String {
        val lines = diff(change.before, change.after)
        return "+${lines.count { it.kind == '+' }} −${lines.count { it.kind == '-' }}"
    }

    fun toJson(change: FileChange): JSONObject = JSONObject()
        .put("id", change.id)
        .put("path", change.path)
        .put("at", change.at)
        .put("author", change.author)
        .put("before", change.before.take(8000))
        .put("after", change.after.take(8000))

    fun fromJson(json: JSONObject): FileChange = FileChange(
        id = json.optLong("id"),
        path = json.optString("path"),
        at = json.optLong("at"),
        author = json.optString("author"),
        before = json.optString("before"),
        after = json.optString("after"),
    )
}
