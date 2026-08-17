package com.mobileforge.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptInterpreterTest {
    @Test
    fun runsUpdateAndMovesActorProxy() {
        val src = """
            function onUpdate(api, dt) {
              const speed = api.object.speed || 8;
              api.move(api.input.x * speed * dt, 0, -api.input.y * speed * dt);
              if (api.input.jump) api.jump(8);
            }
        """.trimIndent()
        val script = ScriptInterpreter(src)
        var x = 0.0
        var jumped = false
        val obj = ScriptInterpreter.Val.Obj(
            get = { key ->
                when (key) {
                    "speed" -> ScriptInterpreter.Val.Num(8.0)
                    else -> ScriptInterpreter.Val.Null
                }
            },
            set = { _, _ -> },
        )
        val input = ScriptInterpreter.Val.Obj(
            get = { key ->
                when (key) {
                    "x" -> ScriptInterpreter.Val.Num(1.0)
                    "y" -> ScriptInterpreter.Val.Num(0.0)
                    "jump" -> ScriptInterpreter.Val.Bool(true)
                    else -> ScriptInterpreter.Val.Null
                }
            },
            set = { _, _ -> },
        )
        val api = ScriptInterpreter.Val.Obj(
            get = { key ->
                when (key) {
                    "object" -> obj
                    "input" -> input
                    "move" -> ScriptInterpreter.Val.Host { args ->
                        x += args[0].num()
                        ScriptInterpreter.Val.Null
                    }
                    "jump" -> ScriptInterpreter.Val.Host {
                        jumped = true
                        ScriptInterpreter.Val.Null
                    }
                    else -> ScriptInterpreter.Val.Null
                }
            },
            set = { _, _ -> },
            call = { name, args ->
                when (name) {
                    "move" -> { x += args[0].num(); ScriptInterpreter.Val.Null }
                    "jump" -> { jumped = true; ScriptInterpreter.Val.Null }
                    else -> ScriptInterpreter.Val.Null
                }
            },
        )
        script.call("onUpdate", mutableMapOf("api" to api, "dt" to ScriptInterpreter.Val.Num(0.5)))
        assertEquals(4.0, x, 0.001)
        assertTrue(jumped)
    }

    @Test
    fun transpileCsMapsApi() {
        val js = CsTranspiler.toJs(
            """
            public class Player {
              public float speed = 6f;
              void Update() {
                Move(input.horizontal * speed, input.vertical * speed);
              }
            }
            """.trimIndent(),
        )
        assertTrue(js.contains("function onUpdate"))
        assertTrue(js.contains("api.move"))
        assertTrue(js.contains("api.input.x"))
    }

    @Test
    fun completionsFindApi() {
        val found = Completions.suggest("api.mo", "Scripts/Player.js")
        assertTrue(found.any { it.label == "api.move" })
    }
}
