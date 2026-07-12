package com.example.nanoagent.agent

interface Tool {
    val name: String
    val description: String
    val parameters: Map<String, String> // parameterName -> description

    suspend fun execute(args: Map<String, String>): String
}
