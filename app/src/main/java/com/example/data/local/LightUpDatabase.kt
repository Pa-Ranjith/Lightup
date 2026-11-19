package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        MealLogEntity::class,
        FoodPresetEntity::class,
        WeightEntryEntity::class,
        DailyActivityEntity::class,
        WatchSnapshotEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class LightUpDatabase : RoomDatabase() {
    abstract fun mealLogDao(): MealLogDao
    abstract fun foodPresetDao(): FoodPresetDao
    abstract fun weightEntryDao(): WeightEntryDao
    abstract fun dailyActivityDao(): DailyActivityDao
    abstract fun watchSnapshotDao(): WatchSnapshotDao

    companion object {
        @Volatile
        private var INSTANCE: LightUpDatabase? = null

        fun getInstance(context: Context): LightUpDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LightUpDatabase::class.java,
                    "lightup_local.db"
                )
                    .fallbackToDestructiveMigration(true)
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            CoroutineScope(Dispatchers.IO).launch {
                                getInstance(context).foodPresetDao().insertPresets(defaultFoodPresets())
                            }
                        }
                    })
                    .build()
                    .also { INSTANCE = it }
            }
        }

        fun defaultFoodPresets(): List<FoodPresetEntity> = listOf(
            FoodPresetEntity(
                name = "Peanut Butter Oat Power Shake",
                category = "High-Calorie Shake",
                servingLabel = "1 tall glass (450 ml)",
                calories = 640,
                proteinGrams = 34,
                carbsGrams = 68,
                fatGrams = 24,
                isFavorite = true
            ),
            FoodPresetEntity(
                name = "Avocado Egg & Sourdough Toast",
                category = "Breakfast",
                servingLabel = "2 slices + 3 eggs",
                calories = 580,
                proteinGrams = 28,
                carbsGrams = 46,
                fatGrams = 30,
                isFavorite = true
            ),
            FoodPresetEntity(
                name = "Greek Yogurt Honey Granola Bowl",
                category = "Breakfast",
                servingLabel = "300 g bowl",
                calories = 460,
                proteinGrams = 26,
                carbsGrams = 56,
                fatGrams = 14,
                isFavorite = false
            ),
            FoodPresetEntity(
                name = "Salmon Jasmine Rice & Sesame Greens",
                category = "Lunch / Dinner",
                servingLabel = "1 full plate",
                calories = 720,
                proteinGrams = 44,
                carbsGrams = 74,
                fatGrams = 26,
                isFavorite = true
            ),
            FoodPresetEntity(
                name = "Chicken Pesto Pasta",
                category = "Lunch / Dinner",
                servingLabel = "1 large bowl",
                calories = 760,
                proteinGrams = 48,
                carbsGrams = 82,
                fatGrams = 24,
                isFavorite = false
            ),
            FoodPresetEntity(
                name = "Beef Burrito Rice Bowl",
                category = "Lunch / Dinner",
                servingLabel = "1 bowl",
                calories = 810,
                proteinGrams = 46,
                carbsGrams = 86,
                fatGrams = 28,
                isFavorite = false
            ),
            FoodPresetEntity(
                name = "Tofu Peanut Noodle Stir-Fry",
                category = "Plant-Based",
                servingLabel = "1 bowl",
                calories = 620,
                proteinGrams = 30,
                carbsGrams = 72,
                fatGrams = 22,
                isFavorite = false
            ),
            FoodPresetEntity(
                name = "Turkey Avocado Wrap",
                category = "Lunch / Dinner",
                servingLabel = "1 large wrap",
                calories = 540,
                proteinGrams = 36,
                carbsGrams = 48,
                fatGrams = 21,
                isFavorite = false
            ),
            FoodPresetEntity(
                name = "Trail Mix & Dark Chocolate",
                category = "Snack",
                servingLabel = "60 g handful",
                calories = 320,
                proteinGrams = 9,
                carbsGrams = 24,
                fatGrams = 21,
                isFavorite = false
            ),
            FoodPresetEntity(
                name = "Cottage Cheese & Pineapple",
                category = "Snack",
                servingLabel = "220 g cup",
                calories = 260,
                proteinGrams = 24,
                carbsGrams = 22,
                fatGrams = 7,
                isFavorite = false
            ),
            FoodPresetEntity(
                name = "Banana Whey & Almond Smoothie",
                category = "High-Calorie Shake",
                servingLabel = "400 ml",
                calories = 490,
                proteinGrams = 36,
                carbsGrams = 54,
                fatGrams = 13,
                isFavorite = true
            ),
            FoodPresetEntity(
                name = "Overnight Chia Protein Oats",
                category = "Breakfast",
                servingLabel = "1 jar",
                calories = 510,
                proteinGrams = 30,
                carbsGrams = 62,
                fatGrams = 15,
                isFavorite = false
            )
        )
    }
}
