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
            // Build a conservative FTS query instead of passing user/model syntax through.
            val terms = query.split(Regex("[^\\p{L}\\p{N}]+"))
                .filter { it.isNotBlank() }
                .take(6)
            if (terms.isEmpty()) return "ERROR: Query contains no searchable terms."
            val formattedQuery = terms.joinToString(" AND ") { "$it*" }
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
                    val entriesStr = fallbackResults.take(3).joinToString("\n\n") { entry ->
                        "Title: ${entry.title}\nContent: ${entry.content.take(2_000)}"
                    }
                    "Found matching articles in database (fuzzy match):\n\n$entriesStr"
                }
            } else {
                val entriesStr = results.take(3).joinToString("\n\n") { entry ->
                    "Title: ${entry.title}\nContent: ${entry.content.take(2_000)}"
                }
                "Found matching articles in database (FTS match):\n\n$entriesStr"
            }
        } catch (e: Exception) {
            "ERROR: Knowledge base search failed: ${e.message}"
        }
    }
}
