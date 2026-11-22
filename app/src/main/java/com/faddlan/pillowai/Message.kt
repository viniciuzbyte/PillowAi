package com.faddlan.pillowai

class Message(
    val role: String,
    val text: String,
    var animate: Boolean = false,
    val typing: Boolean = false
)
