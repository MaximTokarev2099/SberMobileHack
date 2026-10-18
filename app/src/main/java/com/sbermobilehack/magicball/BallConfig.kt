package com.sbermobilehack.magicball

import android.content.Context
import android.graphics.Color
import org.json.JSONObject
import java.time.LocalDate

enum class Season(val label: String, val glow: Long, val particles: List<String>) {
    SUMMER("Лето", 0xFF5DDCFF, emptyList()),
    AUTUMN("Осень", 0xFFFFC35C, listOf("autumn_maple", "autumn_oak", "autumn_birch")),
    WINTER("Зима", 0xFFB7EEFF, listOf("winter_snowflake_1", "winter_snowflake_2", "winter_snowflake_3")),
    SPRING("Весна", 0xFFB5E88A, listOf("spring_daisy", "spring_petal", "spring_leaf", "spring_pink_flower", "spring_lilac_flower"));

    val asset get() = "balls/magic_ball_${name.lowercase(java.util.Locale.ROOT)}.svg"
}

data class BallConfig(
    val season: Season = Season.AUTUMN,
    val particleCount: Int = 20,
    val answers: List<String> = listOf("Всё получится!"),
    val glowColors: Map<Season, Int> = emptyMap()
) {
    companion object {
        fun load(context: Context): BallConfig = runCatching {
            val json = JSONObject(context.assets.open("config.json").bufferedReader().use { it.readText() })
            val answers = json.optJSONArray("answers")
            val validAnswers = (0 until (answers?.length() ?: 0)).mapNotNull { index ->
                (answers?.opt(index) as? String)?.trim()?.takeIf { it.isNotEmpty() }
            }
            val season = if (json.optBoolean("autoSeason")) {
                when (LocalDate.now().monthValue) {
                    in 3..5 -> Season.SPRING
                    in 6..8 -> Season.SUMMER
                    in 9..11 -> Season.AUTUMN
                    else -> Season.WINTER
                }
            } else Season.entries.firstOrNull { it.name.equals(json.optString("season"), true) } ?: Season.AUTUMN
            val colors = Season.entries.mapNotNull { item ->
                val color = json.optJSONObject("glowColors")?.optString(item.name.lowercase(java.util.Locale.ROOT))
                runCatching { item to Color.parseColor(color ?: "") }.getOrNull()
            }.toMap()
            BallConfig(season, json.optInt("particleCount", 20).coerceIn(0, 35),
                validAnswers.ifEmpty { listOf("Всё получится!") }, colors)
        }.getOrDefault(BallConfig())
    }
}

fun nextAnswer(answers: List<String>, previous: String): String =
    answers.filter { it != previous }.ifEmpty { answers }.randomOrNull() ?: "Всё получится!"
