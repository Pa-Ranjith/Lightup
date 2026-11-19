package com.example.ai

import android.util.Log
import com.example.model.GoalMode
import com.example.model.MealSlot
import com.example.model.SuggestedMeal
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "AiMealRepository"

interface AiCoachService {
    suspend fun suggestMeals(
        goalMode: GoalMode,
        remainingCalories: Int,
        remainingProteinG: Int,
        remainingCarbsG: Int,
        remainingFatG: Int,
        dietaryTags: Set<String>,
        cravingPrompt: String
    ): Result<List<SuggestedMeal>>

    fun streamCoachReply(
        question: String,
        history: List<Pair<Boolean, String>> = emptyList(),
        goalMode: GoalMode,
        consumedCalories: Int,
        targetCalories: Int,
        remainingProteinG: Int,
        remainingCarbsG: Int,
        remainingFatG: Int,
        waterMl: Int,
        waterTargetMl: Int,
        currentWeightLabel: String,
        targetWeightLabel: String,
        dietaryTags: Set<String>
    ): Flow<String>
}

class AiMealRepository : AiCoachService {

    private val mealSuggestionSchema = Schema.obj(
        properties = mapOf(
            "meals" to Schema.array(
                description = "Three personalized meal suggestions fitting the user's remaining macros and goal mode",
                items = Schema.obj(
                    properties = mapOf(
                        "title" to Schema.string(description = "Appetizing, clear meal name"),
                        "calories" to Schema.integer(description = "Total kilocalories"),
                        "proteinGrams" to Schema.integer(description = "Protein in grams"),
                        "carbsGrams" to Schema.integer(description = "Carbohydrates in grams"),
                        "fatGrams" to Schema.integer(description = "Fats in grams"),
                        "prepMinutes" to Schema.integer(description = "Estimated prep time in minutes"),
                        "mealSlot" to Schema.enumeration(
                            values = listOf("BREAKFAST", "LUNCH", "DINNER", "SNACK"),
                            description = "Best matching meal slot"
                        ),
                        "ingredients" to Schema.array(
                            items = Schema.string(),
                            description = "3 to 5 concise ingredients with quantities"
                        ),
                        "rationale" to Schema.string(description = "Short 1-sentence reason why this fits their goal")
                    )
                )
            )
        )
    )

    private val structuredMealModel by lazy {
        val config = generationConfig {
            responseMimeType = "application/json"
            responseSchema = mealSuggestionSchema
        }
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = config
        )
    }

    private val coachChatModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = content {
                text(
                    "You are LightUp Coach, an encouraging, practical nutrition and healthy weight-progression assistant. " +
                        "Give concise, actionable, science-backed advice tailored to the user's daily calorie, macro, hydration, and weight metrics. " +
                        "Use clear bullet points and exact gram/kcal numbers when helpful."
                )
            }
        )
    }

    override suspend fun suggestMeals(
        goalMode: GoalMode,
        remainingCalories: Int,
        remainingProteinG: Int,
        remainingCarbsG: Int,
        remainingFatG: Int,
        dietaryTags: Set<String>,
        cravingPrompt: String
    ): Result<List<SuggestedMeal>> {
        val safeRemainingCals = remainingCalories.coerceAtLeast(250)
        val safeProtein = remainingProteinG.coerceAtLeast(15)
        val safeCarbs = remainingCarbsG.coerceAtLeast(20)
        val safeFat = remainingFatG.coerceAtLeast(8)
        val dietNotes = if (dietaryTags.isEmpty()) "None" else dietaryTags.joinToString(", ")
        val userPreference = cravingPrompt.trim().ifBlank { "No specific craving, keep prep practical and delicious." }

        val prompt = """
            You are LightUp's nutrition coach. Suggest 3 distinct, realistic meals or nutrient-dense snacks.
            User goal mode: ${goalMode.title} (${goalMode.badgeText}).
            Remaining daily budget:
            - Calories: $safeRemainingCals kcal
            - Protein: $safeProtein g
            - Carbs: $safeCarbs g
            - Fat: $safeFat g
            Dietary preferences: $dietNotes
            User request / craving: $userPreference

            Each suggested meal should sensibly contribute toward the remaining calorie and macro targets (around 25% to 75% of remaining calories per meal, or an ideal calorie-dense option for Weight Gain).
        """.trimIndent()

        return try {
            val response = withTimeout(60_000L) {
                structuredMealModel.generateContent(prompt)
            }
            val jsonString = response.text
            if (jsonString.isNullOrBlank()) {
                return Result.failure(IllegalStateException("Gemini returned an empty response. Please try again."))
            }

            val parsed = parseMealsJson(jsonString)
            if (parsed.isEmpty()) {
                Result.failure(IllegalStateException("No meal suggestions were returned. Please try again."))
            } else {
                Result.success(parsed)
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI meal suggestion timed out", e)
            Result.failure(Exception("Meal suggestion timed out after 60 seconds. Please check your connection and retry.", e))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI meal generation failed", e)
            Result.failure(Exception(e.localizedMessage ?: "Unable to generate meal ideas right now.", e))
        }
    }

    override fun streamCoachReply(
        question: String,
        history: List<Pair<Boolean, String>>,
        goalMode: GoalMode,
        consumedCalories: Int,
        targetCalories: Int,
        remainingProteinG: Int,
        remainingCarbsG: Int,
        remainingFatG: Int,
        waterMl: Int,
        waterTargetMl: Int,
        currentWeightLabel: String,
        targetWeightLabel: String,
        dietaryTags: Set<String>
    ): Flow<String> = flow {
        val contextPrompt = """
            Current user snapshot:
            - Goal Mode: ${goalMode.title} (${goalMode.badgeText})
            - Weight: $currentWeightLabel (Goal: $targetWeightLabel)
            - Today's Calories: $consumedCalories / $targetCalories kcal
            - Remaining Macros: ${remainingProteinG}g protein, ${remainingCarbsG}g carbs, ${remainingFatG}g fat
            - Hydration: $waterMl / $waterTargetMl ml
            - Dietary Tags: ${if (dietaryTags.isEmpty()) "None" else dietaryTags.joinToString(", ")}

            User question: $question
        """.trimIndent()

        val chatHistory = history
            .takeLast(12)
            .filter { it.second.isNotBlank() }
            .map { (isUser, message) ->
                content(role = if (isUser) "user" else "model") {
                    text(message)
                }
            }

        val accumulated = StringBuilder()
        withTimeout(60_000L) {
            val chat = coachChatModel.startChat(history = chatHistory)
            chat.sendMessageStream(contextPrompt).collect { chunk ->
                val text = chunk.text.orEmpty()
                if (text.isNotEmpty()) {
                    accumulated.append(text)
                    emit(accumulated.toString())
                }
            }
        }
        if (accumulated.isBlank()) {
            throw IllegalStateException("Gemini returned an empty reply.")
        }
    }

    private fun parseMealsJson(jsonString: String): List<SuggestedMeal> {
        val root = JSONObject(jsonString)
        val mealsArray = root.getJSONArray("meals")
        return buildList {
            for (i in 0 until mealsArray.length()) {
                val obj = mealsArray.getJSONObject(i)
                val slotStr = obj.optString("mealSlot", "SNACK")
                val slot = MealSlot.entries.firstOrNull { it.name == slotStr } ?: MealSlot.SNACK
                val ingredientsJson = obj.optJSONArray("ingredients")
                val ingredients = buildList {
                    if (ingredientsJson != null) {
                        for (j in 0 until ingredientsJson.length()) {
                            add(ingredientsJson.getString(j))
                        }
                    }
                }
                add(
                    SuggestedMeal(
                        title = obj.getString("title"),
                        calories = obj.getInt("calories"),
                        proteinGrams = obj.getInt("proteinGrams"),
                        carbsGrams = obj.getInt("carbsGrams"),
                        fatGrams = obj.getInt("fatGrams"),
                        prepMinutes = obj.optInt("prepMinutes", 15),
                        mealSlot = slot,
                        ingredients = ingredients,
                        rationale = obj.optString("rationale", "Balanced macro match for your daily goal.")
                    )
                )
            }
        }
    }
}
