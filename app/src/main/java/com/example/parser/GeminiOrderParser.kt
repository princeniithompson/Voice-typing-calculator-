package com.example.parser

import android.util.Log
import com.example.data.model.MenuItemEntity
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

data class AiParsedItem(
    val itemName: String,
    val quantity: Int,
    val action: String // "ADD" or "REMOVE"
)

class GeminiOrderParser {

    companion object {
        private const val TAG = "GeminiOrderParser"
    }

    private val model by lazy {
        try {
            val config = generationConfig {
                responseMimeType = "application/json"
            }
            Firebase.ai.generativeModel(
                modelName = "gemini-3.8-flash",
                generationConfig = config
            )
        } catch (e: Exception) {
            Log.w(TAG, "Firebase AI Logic not initialized; falling back to on-device parser", e)
            null
        }
    }

    suspend fun parseWithGemini(
        transcript: String,
        menuItems: List<MenuItemEntity>
    ): List<ParsedOrderItem>? {
        if (transcript.isBlank() || menuItems.isEmpty()) return null
        val generativeModel = model ?: return null

        val menuContext = menuItems.joinToString("\n") {
            "- ${it.name} (GHS ${it.price}, unit: ${it.unitLabel}, aliases: ${it.aliases})"
        }

        val prompt = """
            You are a Ghanaian restaurant point-of-sale voice order parser.
            Available menu items:
            $menuContext

            Customer spoken input: "$transcript"

            Extract each ordered item and return a strict JSON array of objects:
            [
              {
                "matchedItem": "exact name from available menu list",
                "quantity": 1,
                "action": "ADD"
              }
            ]
            If the customer says remove/delete/cancel, set "action" to "REMOVE".
            If an item is not on the menu, ignore it. Output only valid JSON.
        """.trimIndent()

        return try {
            val response = withTimeout(15_000L) {
                generativeModel.generateContent(prompt)
            }
            val jsonText = response.text?.trim() ?: return null
            val array = JSONArray(jsonText)
            val parsedList = mutableListOf<ParsedOrderItem>()

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val matchedName = obj.optString("matchedItem")
                val quantity = obj.optInt("quantity", 1).coerceAtLeast(1)
                val action = obj.optString("action", "ADD")

                val matchedMenuItem = menuItems.find {
                    it.name.equals(matchedName, ignoreCase = true)
                } ?: menuItems.find {
                    it.name.contains(matchedName, ignoreCase = true)
                }

                if (matchedMenuItem != null) {
                    parsedList.add(
                        ParsedOrderItem(
                            menuItem = matchedMenuItem,
                            quantity = quantity,
                            isRemoval = action.equals("REMOVE", ignoreCase = true),
                            matchedConfidence = 0.98f
                        )
                    )
                }
            }

            if (parsedList.isNotEmpty()) parsedList else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Gemini parsing fallback triggered: ${e.message}")
            null
        }
    }
}
