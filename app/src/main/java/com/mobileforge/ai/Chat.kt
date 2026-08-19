package com.mobileforge.ai

data class ChatTurn(val role: String, val content: String)

data class ChatReply(
    val text: String,
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val model: String = "",
    val thinking: String = "",
)

data class StreamDelta(
    val text: String = "",
    val thinking: String = "",
)
