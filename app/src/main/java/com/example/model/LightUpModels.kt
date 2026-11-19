package com.example.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

enum class GoalMode(
    val title: String,
    val shortLabel: String,
    val badgeText: String,
    val description: String
) {
    GAIN(
        title = "Weight Gain",
        shortLabel = "Gain",
        badgeText = "Caloric Surplus",
        description = "Steady caloric surplus for lean mass & strength"
    ),
    MAINTAIN(
        title = "Maintain",
        shortLabel = "Maintain",
        badgeText = "Energy Balance",
        description = "Fuel daily performance & keep current weight steady"
    ),
    CUT(
        title = "Lean Cut",
        shortLabel = "Cut",
        badgeText = "Gentle Deficit",
        description = "Controlled caloric deficit while preserving protein"
    )
}

enum class SurplusPace(
    val label: String,
    val kcalDelta: Int,
    val goalMode: GoalMode
) {
    LEAN_GAIN("Lean Gain (+250 kcal)", 250, GoalMode.GAIN),
    CLASSIC_GAIN("Classic Gain (+500 kcal)", 500, GoalMode.GAIN),
    BALANCED_MAINTAIN("Steady Balance (0 kcal)", 0, GoalMode.MAINTAIN),
    GENTLE_CUT("Gentle Cut (-350 kcal)", -350, GoalMode.CUT)
}

enum class MealSlot(val displayName: String, val sortOrder: Int) {
    BREAKFAST("Breakfast", 0),
    LUNCH("Lunch", 1),
    DINNER("Dinner", 2),
    SNACK("Snacks", 3)
}

enum class ActivityLevel(val label: String, val multiplier: Float) {
    SEDENTARY("Light / Desk", 1.3f),
    LIGHT("1-3 Workouts / wk", 1.45f),
    MODERATE("3-5 Workouts / wk", 1.6f),
    ACTIVE("6+ Active Days", 1.75f)
}

enum class TrendTimeframe(val label: String, val days: Int?) {
    SEVEN_DAYS("7D", 7),
    FOURTEEN_DAYS("14D", 14),
    THIRTY_DAYS("30D", 30),
    ALL("All", null)
}

data class SuggestedMeal(
    val title: String,
    val calories: Int,
    val proteinGrams: Int,
    val carbsGrams: Int,
    val fatGrams: Int,
    val prepMinutes: Int,
    val mealSlot: MealSlot,
    val ingredients: List<String>,
    val rationale: String
)

data class AchievementBadge(
    val id: String,
    val title: String,
    val subtitle: String,
    val unlocked: Boolean
)

object DateKeyUtils {
    private val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val displayFormatter = SimpleDateFormat("EEE, MMM d", Locale.US)
    private val shortFormatter = SimpleDateFormat("MMM d", Locale.US)

    fun todayKey(): String = synchronized(formatter) {
        formatter.format(Calendar.getInstance().time)
    }

    fun keyDaysAgo(daysAgo: Int): String = synchronized(formatter) {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
        formatter.format(cal.time)
    }

    fun formatDisplayDate(dateKey: String): String {
        val date = synchronized(formatter) { runCatching { formatter.parse(dateKey) }.getOrNull() }
            ?: return dateKey
        return synchronized(displayFormatter) { displayFormatter.format(date) }
    }

    fun formatShortDate(dateKey: String): String {
        val date = synchronized(formatter) { runCatching { formatter.parse(dateKey) }.getOrNull() }
            ?: return dateKey
        return synchronized(shortFormatter) { shortFormatter.format(date) }
    }
}

object UnitFormatter {
    private const val KG_TO_LB = 2.20462f
    private const val ML_TO_OZ = 0.033814f

    fun formatWeight(weightKg: Float, useMetric: Boolean): String {
        return if (useMetric) {
            String.format(Locale.US, "%.1f kg", weightKg)
        } else {
            String.format(Locale.US, "%.1f lb", weightKg * KG_TO_LB)
        }
    }

    fun formatWeightNumber(weightKg: Float, useMetric: Boolean): String {
        return if (useMetric) {
            String.format(Locale.US, "%.1f", weightKg)
        } else {
            String.format(Locale.US, "%.1f", weightKg * KG_TO_LB)
        }
    }

    fun weightUnit(useMetric: Boolean): String = if (useMetric) "kg" else "lb"

    fun formatWeightDelta(deltaKg: Float, useMetric: Boolean): String {
        val value = if (useMetric) deltaKg else deltaKg * KG_TO_LB
        val sign = if (value >= 0f) "+" else ""
        return String.format(Locale.US, "%s%.1f %s", sign, value, weightUnit(useMetric))
    }

    fun formatWater(waterMl: Int, useMetric: Boolean): String {
        return if (useMetric) {
            "$waterMl ml"
        } else {
            val oz = (waterMl * ML_TO_OZ).roundToInt()
            "$oz oz"
        }
    }

    fun formatWaterIncrement(incrementMl: Int, useMetric: Boolean): String {
        return if (useMetric) {
            "${incrementMl}ml"
        } else {
            "${(incrementMl * ML_TO_OZ).roundToInt()}oz"
        }
    }
}
