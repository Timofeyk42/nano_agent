package com.example.nanoagent.tools

import com.example.nanoagent.agent.Tool
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class InternetTool : Tool {
    override val name = "searchInternet"
    override val description = "Searches the web for information or fetches text contents from a given query."
    override val parameters = mapOf("query" to "Search query terms or a full URL path to load")

    private val client = HttpClient(OkHttp)

    override suspend fun execute(args: Map<String, String>): String = withContext(Dispatchers.IO) {
        val query = args["query"] ?: return@withContext "ERROR: Missing 'query' parameter."

        if (query.startsWith("http://") || query.startsWith("https://")) {
            return@withContext try {
                val response = client.get(query) {
                    header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                }
                val body = response.bodyAsText()
                val cleanText = body.replace(Regex("<[^>]*>"), " ")
                    .replace(Regex("\\s+"), " ")
                    .take(1500)
                "Content from $query:\n$cleanText..."
            } catch (e: Exception) {
                "ERROR: Failed to fetch URL contents: ${e.message}"
            }
        }

        // Live Weather query interceptor
        val lowerQuery = query.lowercase()
        if ((lowerQuery.contains("погода") || lowerQuery.contains("weather")) && !query.contains("http")) {
            val city = query.replace(Regex("(?i)(погода|weather|в|in|сейчас|today|на сегодня|на неделю)"), "").trim()
            if (city.isNotEmpty()) {
                try {
                    val encodedCity = URLEncoder.encode(city, StandardCharsets.UTF_8.toString())
                    val lang = if (lowerQuery.contains("погода")) "ru" else "en"
                    // Query wttr.in for a beautiful single-line weather report
                    val weatherUrl = "https://wttr.in/$encodedCity?format=3&lang=$lang"
                    val response = client.get(weatherUrl) {
                        header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    }
                    val weatherText = response.bodyAsText().trim()
                    if (weatherText.isNotEmpty() && !weatherText.contains("Unknown location") && !weatherText.contains("Error")) {
                        return@withContext "Live Weather info for $city:\n$weatherText"
                    }
                } catch (e: Exception) {
                    // Fall back to DuckDuckGo search if wttr.in fails
                }
            }
        }

        // Web search (using duckduckgo html search with a user agent)
        return@withContext try {
            val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
            val searchUrl = "https://html.duckduckgo.com/html/?q=$encodedQuery"
            val response = client.get(searchUrl) {
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            }
            val html = response.bodyAsText()

            // Extract titles
            val titles = mutableListOf<String>()
            val titleRegex = Regex("class=\"result__a\"[^>]*>(.*?)</a>")
            val titleMatches = titleRegex.findAll(html)
            for (match in titleMatches) {
                val clean = match.groupValues[1].replace(Regex("<[^>]*>"), "").trim()
                if (clean.isNotEmpty()) {
                    titles.add(clean)
                }
            }

            // Extract snippets
            val snippets = mutableListOf<String>()
            val snippetRegex = Regex("class=\"result[_-]+snippet\"[^>]*>(.*?)<\\/(div|td|span|a|p)>")
            val snippetMatches = snippetRegex.findAll(html)
            for (match in snippetMatches) {
                val clean = match.groupValues[1].replace(Regex("<[^>]*>"), "").trim()
                if (clean.isNotEmpty()) {
                    snippets.add(clean)
                }
            }

            val resultsText = StringBuilder()
            val limit = minOf(titles.size, snippets.size, 3)
            for (i in 0 until limit) {
                resultsText.append("${i + 1}. Title: ${titles[i]}\nSnippet: ${snippets[i]}\n\n")
            }

            if (resultsText.isEmpty()) {
                "DuckDuckGo search returned 0 results for '$query'. Please try a different query."
            } else {
                "Web search results for '$query':\n\n$resultsText"
            }
        } catch (e: Exception) {
            "ERROR: Web search request failed. Check internet connection. Error: ${e.message}"
        }
    }
}
