package com.mobileforge.engine

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineKitTest {
    @Test
    fun aliases() {
        assertEquals("Rigidbody", EngineKit.alias("RigidBody3D"))
        assertEquals("ParticleSystem", EngineKit.alias("Niagara"))
        val extra = JSONObject()
        addComponent(extra, "CharacterBody3D")
        assertTrue(hasComponent(extra, "CharacterController"))
    }
}
