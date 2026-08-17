package com.mobileforge.engine

/**
 * Tiny JS-subset interpreter for MobileForge Play scripts.
 * Supports functions, if, assignments, members, calls, arithmetic and || / &&.
 */
class ScriptInterpreter(source: String) {
    private val functions: Map<String, Fn>

    init {
        val tokens = tokenize(source)
        functions = Parser(tokens).parseProgram()
    }

    fun has(name: String): Boolean = functions.containsKey(name)

    fun call(name: String, env: MutableMap<String, Val>): Val {
        val fn = functions[name] ?: return Val.Null
        val local = env.toMutableMap()
        fn.params.forEachIndexed { i, p ->
            if (p !in local) {
                val extras = (env["__args"] as? Val.Arr)?.items
                if (extras != null && i < extras.size) local[p] = extras[i]
            }
        }
        return evalBlock(fn.body, local)
    }

    sealed class Val {
        data class Num(val v: Double) : Val()
        data class Str(val v: String) : Val()
        data class Bool(val v: Boolean) : Val()
        data class Obj(
            val get: (String) -> Val,
            val set: (String, Val) -> Unit,
            val call: ((String, List<Val>) -> Val)? = null,
        ) : Val()
        data class Host(val impl: (List<Val>) -> Val) : Val()
        data class Arr(val items: List<Val>) : Val()
        object Null : Val()

        fun truthy(): Boolean = when (this) {
            is Num -> v != 0.0
            is Str -> v.isNotEmpty()
            is Bool -> v
            Null -> false
            else -> true
        }

        fun num(): Double = when (this) {
            is Num -> v
            is Bool -> if (v) 1.0 else 0.0
            is Str -> v.toDoubleOrNull() ?: 0.0
            else -> 0.0
        }

        fun str(): String = when (this) {
            is Str -> v
            is Num -> if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
            is Bool -> v.toString()
            Null -> "null"
            else -> toString()
        }
    }

    private data class Fn(val params: List<String>, val body: List<Stmt>)

    private sealed class Stmt {
        data class Block(val stmts: List<Stmt>) : Stmt()
        data class Let(val name: String, val expr: Expr) : Stmt()
        data class If(val cond: Expr, val then: Stmt, val orElse: Stmt?) : Stmt()
        data class Return(val expr: Expr?) : Stmt()
        data class ExprStmt(val expr: Expr) : Stmt()
    }

    private sealed class Expr {
        data class Literal(val value: Val) : Expr()
        data class Ident(val name: String) : Expr()
        data class Binary(val op: String, val left: Expr, val right: Expr) : Expr()
        data class Unary(val op: String, val expr: Expr) : Expr()
        data class Assign(val target: Expr, val value: Expr) : Expr()
        data class Member(val obj: Expr, val name: String) : Expr()
        data class Call(val callee: Expr, val args: List<Expr>) : Expr()
    }

    private data class Tok(val kind: String, val text: String)

    private class ReturnSignal(val value: Val) : RuntimeException()

    private fun evalBlock(stmts: List<Stmt>, env: MutableMap<String, Val>): Val {
        var last: Val = Val.Null
        try {
            stmts.forEach { last = evalStmt(it, env) }
        } catch (r: ReturnSignal) {
            return r.value
        }
        return last
    }

    private fun evalStmt(stmt: Stmt, env: MutableMap<String, Val>): Val = when (stmt) {
        is Stmt.Block -> evalBlock(stmt.stmts, env)
        is Stmt.Let -> eval(stmt.expr, env).also { env[stmt.name] = it }
        is Stmt.If -> if (eval(stmt.cond, env).truthy()) evalStmt(stmt.then, env) else stmt.orElse?.let { evalStmt(it, env) } ?: Val.Null
        is Stmt.Return -> throw ReturnSignal(stmt.expr?.let { eval(it, env) } ?: Val.Null)
        is Stmt.ExprStmt -> eval(stmt.expr, env)
    }

    private fun eval(expr: Expr, env: MutableMap<String, Val>): Val = when (expr) {
        is Expr.Literal -> expr.value
        is Expr.Ident -> env[expr.name] ?: mathHost(expr.name) ?: Val.Null
        is Expr.Unary -> {
            val v = eval(expr.expr, env)
            when (expr.op) {
                "-" -> Val.Num(-v.num())
                "!" -> Val.Bool(!v.truthy())
                else -> v
            }
        }
        is Expr.Binary -> {
            if (expr.op == "||") {
                val l = eval(expr.left, env)
                if (l.truthy()) l else eval(expr.right, env)
            } else if (expr.op == "&&") {
                val l = eval(expr.left, env)
                if (!l.truthy()) l else eval(expr.right, env)
            } else {
                val l = eval(expr.left, env)
                val r = eval(expr.right, env)
                when (expr.op) {
                    "+" -> if (l is Val.Str || r is Val.Str) Val.Str(l.str() + r.str()) else Val.Num(l.num() + r.num())
                    "-" -> Val.Num(l.num() - r.num())
                    "*" -> Val.Num(l.num() * r.num())
                    "/" -> Val.Num(if (r.num() == 0.0) 0.0 else l.num() / r.num())
                    "%" -> Val.Num(l.num() % r.num())
                    "<" -> Val.Bool(l.num() < r.num())
                    ">" -> Val.Bool(l.num() > r.num())
                    "<=" -> Val.Bool(l.num() <= r.num())
                    ">=" -> Val.Bool(l.num() >= r.num())
                    "==", "===" -> Val.Bool(l.str() == r.str() || l.num() == r.num())
                    "!=", "!==" -> Val.Bool(l.str() != r.str() && l.num() != r.num())
                    else -> Val.Null
                }
            }
        }
        is Expr.Member -> {
            when (val obj = eval(expr.obj, env)) {
                is Val.Obj -> obj.get(expr.name)
                else -> Val.Null
            }
        }
        is Expr.Assign -> {
            val value = eval(expr.value, env)
            when (val target = expr.target) {
                is Expr.Ident -> env[target.name] = value
                is Expr.Member -> {
                    val obj = eval(target.obj, env)
                    if (obj is Val.Obj) obj.set(target.name, value)
                }
                else -> {}
            }
            value
        }
        is Expr.Call -> {
            val args = expr.args.map { eval(it, env) }
            when (val callee = eval(expr.callee, env)) {
                is Val.Host -> callee.impl(args)
                is Val.Obj -> {
                    val name = (expr.callee as? Expr.Member)?.name
                    if (name != null && callee.call != null) callee.call.invoke(name, args) else Val.Null
                }
                else -> {
                    if (expr.callee is Expr.Member) {
                        val obj = eval(expr.callee.obj, env)
                        if (obj is Val.Obj && obj.call != null) obj.call.invoke(expr.callee.name, args) else Val.Null
                    } else Val.Null
                }
            }
        }
    }

    private fun mathHost(name: String): Val? = when (name) {
        "Math" -> Val.Obj(
            get = { key ->
                Val.Host { args ->
                    val a = args.getOrNull(0)?.num() ?: 0.0
                    val b = args.getOrNull(1)?.num() ?: 0.0
                    Val.Num(
                        when (key) {
                            "sin" -> kotlin.math.sin(a)
                            "cos" -> kotlin.math.cos(a)
                            "abs" -> kotlin.math.abs(a)
                            "max" -> maxOf(a, b)
                            "min" -> minOf(a, b)
                            "sqrt" -> kotlin.math.sqrt(a)
                            "floor" -> kotlin.math.floor(a)
                            "round" -> kotlin.math.round(a)
                            else -> 0.0
                        },
                    )
                }
            },
            set = { _, _ -> },
        )
        else -> null
    }

    private fun tokenize(src: String): List<Tok> {
        val out = mutableListOf<Tok>()
        var i = 0
        fun peek(): Char = if (i < src.length) src[i] else '\u0000'
        while (i < src.length) {
            val c = src[i]
            when {
                c.isWhitespace() -> i++
                c == '/' && peekNext(src, i) == '/' -> while (i < src.length && src[i] != '\n') i++
                c == '/' && peekNext(src, i) == '*' -> {
                    i += 2
                    while (i < src.length - 1 && !(src[i] == '*' && src[i + 1] == '/')) i++
                    i += 2
                }
                c == '"' || c == '\'' -> {
                    val q = c
                    i++
                    val buf = StringBuilder()
                    while (i < src.length && src[i] != q) {
                        if (src[i] == '\\' && i + 1 < src.length) {
                            buf.append(src[i + 1]); i += 2
                        } else buf.append(src[i++])
                    }
                    i++
                    out += Tok("str", buf.toString())
                }
                c.isDigit() -> {
                    val s = i
                    while (i < src.length && (src[i].isDigit() || src[i] == '.')) i++
                    out += Tok("num", src.substring(s, i))
                }
                c.isLetter() || c == '_' || c == '$' -> {
                    val s = i
                    while (i < src.length && (src[i].isLetterOrDigit() || src[i] == '_' || src[i] == '$')) i++
                    out += Tok("id", src.substring(s, i))
                }
                src.startsWith("===", i) || src.startsWith("!==", i) -> {
                    out += Tok("op", src.substring(i, i + 3)); i += 3
                }
                src.startsWith("==", i) || src.startsWith("!=", i) || src.startsWith("<=", i) ||
                    src.startsWith(">=", i) || src.startsWith("&&", i) || src.startsWith("||", i) -> {
                    out += Tok("op", src.substring(i, i + 2)); i += 2
                }
                c in "+-*/%<>=!()" || c == '{' || c == '}' || c == ',' || c == ';' || c == '.' || c == ':' -> {
                    out += Tok("op", c.toString()); i++
                }
                else -> i++
            }
        }
        out += Tok("eof", "")
        return out
    }

    private fun peekNext(src: String, i: Int): Char = if (i + 1 < src.length) src[i + 1] else '\u0000'

    private class Parser(val tokens: List<Tok>) {
        var p = 0
        fun peek() = tokens[p]
        fun eat(text: String? = null): Tok {
            val t = tokens[p]
            if (text != null && t.text != text) error("expected $text got ${t.text}")
            p++
            return t
        }
        fun match(text: String): Boolean = if (peek().text == text) { p++; true } else false

        fun parseProgram(): Map<String, Fn> {
            val fns = linkedMapOf<String, Fn>()
            while (peek().kind != "eof") {
                if (peek().text == "function") {
                    eat("function")
                    val name = eat().text
                    eat("(")
                    val params = mutableListOf<String>()
                    if (peek().text != ")") {
                        params += eat().text
                        while (match(",")) params += eat().text
                    }
                    eat(")")
                    fns[name] = Fn(params, listOf(parseBlock()))
                } else {
                    p++
                }
            }
            return fns
        }

        fun parseBlock(): Stmt.Block {
            eat("{")
            val stmts = mutableListOf<Stmt>()
            while (peek().text != "}" && peek().kind != "eof") stmts += parseStmt()
            eat("}")
            return Stmt.Block(stmts)
        }

        fun parseStmt(): Stmt {
            return when (peek().text) {
                "{" -> parseBlock()
                "let", "const", "var" -> {
                    eat(); val name = eat().text; eat("="); val e = parseExpr(); match(";"); Stmt.Let(name, e)
                }
                "if" -> {
                    eat("if"); eat("("); val c = parseExpr(); eat(")")
                    val t = parseStmt()
                    val e = if (match("else")) parseStmt() else null
                    Stmt.If(c, t, e)
                }
                "return" -> {
                    eat("return")
                    val e = if (peek().text == ";" || peek().text == "}") null else parseExpr()
                    match(";")
                    Stmt.Return(e)
                }
                else -> Stmt.ExprStmt(parseExpr()).also { match(";") }
            }
        }

        fun parseExpr(): Expr = parseAssign()
        fun parseAssign(): Expr {
            val left = parseOr()
            return if (match("=")) Expr.Assign(left, parseAssign()) else left
        }
        fun parseOr(): Expr {
            var e = parseAnd()
            while (match("||")) e = Expr.Binary("||", e, parseAnd())
            return e
        }
        fun parseAnd(): Expr {
            var e = parseEq()
            while (match("&&")) e = Expr.Binary("&&", e, parseEq())
            return e
        }
        fun parseEq(): Expr {
            var e = parseCmp()
            while (peek().text in setOf("==", "===", "!=", "!==")) {
                val op = eat().text
                e = Expr.Binary(op, e, parseCmp())
            }
            return e
        }
        fun parseCmp(): Expr {
            var e = parseAdd()
            while (peek().text in setOf("<", ">", "<=", ">=")) {
                val op = eat().text
                e = Expr.Binary(op, e, parseAdd())
            }
            return e
        }
        fun parseAdd(): Expr {
            var e = parseMul()
            while (peek().text == "+" || peek().text == "-") {
                val op = eat().text
                e = Expr.Binary(op, e, parseMul())
            }
            return e
        }
        fun parseMul(): Expr {
            var e = parseUnary()
            while (peek().text == "*" || peek().text == "/" || peek().text == "%") {
                val op = eat().text
                e = Expr.Binary(op, e, parseUnary())
            }
            return e
        }
        fun parseUnary(): Expr {
            if (peek().text == "-" || peek().text == "!") return Expr.Unary(eat().text, parseUnary())
            return parsePost()
        }
        fun parsePost(): Expr {
            var e = parsePrimary()
            while (true) {
                e = when {
                    match(".") -> Expr.Member(e, eat().text)
                    match("(") -> {
                        val args = mutableListOf<Expr>()
                        if (peek().text != ")") {
                            args += parseExpr()
                            while (match(",")) args += parseExpr()
                        }
                        eat(")")
                        Expr.Call(e, args)
                    }
                    else -> return e
                }
            }
        }
        fun parsePrimary(): Expr {
            val t = peek()
            return when {
                t.kind == "num" -> Expr.Literal(Val.Num(eat().text.toDouble())).also { }
                t.kind == "str" -> Expr.Literal(Val.Str(eat().text))
                t.text == "true" -> { eat(); Expr.Literal(Val.Bool(true)) }
                t.text == "false" -> { eat(); Expr.Literal(Val.Bool(false)) }
                t.text == "null" || t.text == "undefined" -> { eat(); Expr.Literal(Val.Null) }
                t.text == "(" -> { eat("("); val e = parseExpr(); eat(")"); e }
                t.kind == "id" -> Expr.Ident(eat().text)
                else -> { eat(); Expr.Literal(Val.Null) }
            }
        }
    }
}
