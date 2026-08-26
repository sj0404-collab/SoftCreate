package com.mobileforge.engine

object CsTranspiler {
    fun toJs(source: String): String {
        if (source.isBlank()) return ""
        val methods = linkedMapOf<String, String>()
        val methodRe = Regex("""(?:public|private|protected)?\s*(?:void|int|float|bool|string)\s+([A-Za-z_][A-Za-z0-9_]*)\s*\(([^)]*)\)\s*\{""")
        methodRe.findAll(source).forEach { match ->
            val name = match.groupValues[1]
            val start = match.range.last
            val end = matchingBrace(source, start)
            if (end > start) methods[name] = source.substring(start + 1, end)
        }
        val fields = Regex("""(?:public|private|protected)?\s*(?:float|int|bool|string)\s+([A-Za-z_][A-Za-z0-9_]*)\s*=\s*([^;]+);""")
            .findAll(source)
            .map { "api.object.${it.groupValues[1]} = ${jsLiteral(it.groupValues[2])};" }
            .toList()
        return buildString {
            appendLine("function onStart(api) {")
            fields.forEach { appendLine("  $it") }
            appendLine(convertBody(methods["Start"] ?: methods["OnStart"] ?: methods["OnSceneLoaded"].orEmpty()))
            appendLine("}")
            appendLine("function onUpdate(api, dt) {")
            appendLine(convertBody(methods["Update"] ?: methods["OnUpdate"].orEmpty()))
            appendLine("}")
            appendLine("function onCollisionEnter(api, other) {")
            appendLine(convertBody(methods["OnCollisionEnter"].orEmpty()))
            appendLine("}")
            appendLine("function onButtonClick(api) {")
            appendLine(convertBody(methods["OnButtonClick"].orEmpty()))
            appendLine("}")
        }
    }

    fun scriptSource(path: String, source: String): String {
        return if (path.endsWith(".cs", true) || source.contains(Regex("""public\s+class\s+"""))) {
            toJs(source)
        } else {
            source
        }
    }

    fun parseProposal(text: String): List<Pair<String, String>> {
        val files = mutableListOf<Pair<String, String>>()
        val fence = Regex("```([^\\n]*)\\n([\\s\\S]*?)```")
        fence.findAll(text).forEach { match ->
            val header = match.groupValues[1].trim()
            val body = match.groupValues[2].trimEnd() + "\n"
            val fromHeader = Regex("""[\w./-]+\.(?:cs|js|ts|tsx|jsx|kt|java|smali|yml|yaml|cpp|h|c|json|glsl|md|txt|lua|xml|py|html|css|obj|gradle)""", RegexOption.IGNORE_CASE)
                .find(header)?.value.orEmpty()
            val first = body.lineSequence().firstOrNull().orEmpty()
            val fromLine = Regex("""^(?://|#|<!--)\s*([A-Za-z0-9_./-]+\.[A-Za-z0-9]+)""")
                .find(first)?.groupValues?.getOrNull(1).orEmpty()
            files += normalizePath(fromHeader.ifBlank { fromLine }) to body
        }
        if (files.isEmpty() && text.isNotBlank()) files += "" to (text.trim() + "\n")
        return files
    }

    fun guessPath(language: String, event: String, openPath: String?): String {
        if (!openPath.isNullOrBlank()) return openPath
        val ext = when (language.lowercase()) {
            "kotlin", "kt" -> "kt"
            "java" -> "java"
            "smali" -> "smali"
            "tsx", "react" -> "tsx"
            "typescript", "ts" -> "ts"
            "python", "blender", "py" -> "py"
            "yaml", "yml" -> "yml"
            "c++" -> "cpp"
            "glsl" -> "glsl"
            "json" -> "json"
            "javascript", "js" -> "js"
            else -> "cs"
        }
        val base = event.replace(Regex("[^A-Za-z0-9_]"), "").ifBlank { "Generated" }
        val folder = LangKit.folderFor(ext)
        return "$folder/$base.$ext"
    }

    private fun normalizePath(path: String): String {
        if (path.isBlank()) return ""
        var p = path.replace('\\', '/').trimStart('/')
        if (!p.contains('/')) {
            p = if (p.endsWith(".scene.json")) "Scenes/$p"
            else {
                val ext = p.substringAfterLast('.', "")
                if (ext.isNotBlank()) "${LangKit.folderFor(ext)}/$p" else p
            }
        }
        return p
    }

    private fun matchingBrace(text: String, openIndex: Int): Int {
        var depth = 0
        for (i in openIndex until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return -1
    }

    private fun jsLiteral(raw: String): String {
        val v = raw.trim().removeSuffix("f").removeSuffix("F")
        if (v == "true" || v == "false") return v
        if (v.toDoubleOrNull() != null) return v
        return "\"" + v.trim('"') + "\""
    }

    private fun convertBody(body: String): String = body
        .replace(Regex("""\b(float|int|bool|var|new)\b"""), "let")
        .replace(Regex("""\bthis\."""), "api.object.")
        .replace(Regex("""\binput\.horizontal\b"""), "api.input.x")
        .replace(Regex("""\binput\.vertical\b"""), "api.input.y")
        .replace(Regex("""\binput\.jump\b"""), "api.input.jump")
        .replace(Regex("""\binput\.action\b"""), "api.input.action")
        .replace(Regex("""\bMove\s*\("""), "api.move(")
        .replace(Regex("""\bJump\s*\("""), "api.jump(")
        .replace(Regex("""\bAddScore\s*\("""), "api.addScore(")
        .replace(Regex("""\bDestroy\s*\("""), "api.destroy(")
        .replace(Regex("""\bSetPosition\s*\("""), "api.setPosition(")
        .replace(Regex("""\bFind\s*\("""), "api.find(")
        .replace(Regex("""\bLoadScene\s*\("""), "api.loadScene(")
        .replace(Regex("""\bLog\s*\("""), "api.log(")
        .replace(Regex("""(\w+)\s*==\s*""""), "$1 === \"")
        .lines()
        .joinToString("\n") { line -> if (line.isBlank()) "" else "  ${line.trim()}" }
}
