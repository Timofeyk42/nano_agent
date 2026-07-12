package com.example.nanoagent.agent

import android.content.Context
import com.example.nanoagent.data.KnowledgeBaseDao
import com.example.nanoagent.tools.InternetTool
import com.example.nanoagent.tools.JavaScriptTool
import com.example.nanoagent.tools.KnowledgeBaseTool
import com.example.nanoagent.tools.LocationTool
import com.example.nanoagent.tools.SettingsTool
import com.example.nanoagent.tools.TimeTool

class ToolRegistry(context: Context, dao: KnowledgeBaseDao) {
    private val tools = mutableMapOf<String, Tool>()

    init {
        register(LocationTool(context))
        register(TimeTool())
        register(SettingsTool(context))
        register(InternetTool())
        register(KnowledgeBaseTool(dao))
        register(JavaScriptTool(context))
    }

    fun register(tool: Tool) {
        tools[tool.name] = tool
    }

    fun getTool(name: String): Tool? = tools[name]

    fun getAllTools(): Collection<Tool> = tools.values

    fun getToolsInstructions(): String {
        return tools.values.joinToString("\n") { tool ->
            "- Name: `${tool.name}`\n" +
            "  Description: ${tool.description}\n" +
            "  Parameters: ${tool.parameters.entries.joinToString { "${it.key}: ${it.value}" }}"
        }
    }
}
