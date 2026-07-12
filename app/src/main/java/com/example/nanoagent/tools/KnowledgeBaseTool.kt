package com.example.nanoagent.tools

import com.example.nanoagent.agent.Tool
import com.example.nanoagent.data.KnowledgeBaseDao

class KnowledgeBaseTool(private val dao: KnowledgeBaseDao) : Tool {
    override val name = "searchKnowledgeBase"
    override val description = "Queries the local Wikipedia/knowledge base for matches to a query."
    override val parameters = mapOf("query" to "Search term or keyword to match")

    override suspend fun execute(args: Map<String, String>): String {
        val query = args["query"] ?: return "ERROR: Missing 'query' parameter."
        if (query.trim().isEmpty()) {
            return "ERROR: Query is empty."
        }

        return try {
            // Append wildcard to queries for better matches if using FTS
            val formattedQuery = if (!query.contains("*")) "$query*" else query
            val results = dao.search(formattedQuery)

            if (results.isEmpty()) {
                // Try fallback substring match in memory
                val all = dao.getAllEntries()
                val fallbackResults = all.filter {
                    it.title.contains(query, ignoreCase = true) ||
                    it.content.contains(query, ignoreCase = true)
                }

                if (fallbackResults.isEmpty()) {
                    "No matching wiki articles found for query: '$query'."
                } else {
                    val entriesStr = fallbackResults.joinToString("\n\n") { entry ->
                        "Title: ${entry.title}\nContent: ${entry.content}"
                    }
                    "Found matching articles in database (fuzzy match):\n\n$entriesStr"
                }
            } else {
                val entriesStr = results.joinToString("\n\n") { entry ->
                    "Title: ${entry.title}\nContent: ${entry.content}"
                }
                "Found matching articles in database (FTS match):\n\n$entriesStr"
            }
        } catch (e: Exception) {
            "ERROR: Knowledge base search failed: ${e.message}"
        }
    }
}
