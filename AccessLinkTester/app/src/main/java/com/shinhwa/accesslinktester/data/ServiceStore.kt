package com.shinhwa.accesslinktester.data

import android.content.Context
import android.content.SharedPreferences
import com.shinhwa.accesslinktester.model.DoorConfig
import com.shinhwa.accesslinktester.model.RegisteredCard
import com.shinhwa.accesslinktester.model.RegisteredFace
import com.shinhwa.accesslinktester.model.ServiceSettings
import org.json.JSONArray
import org.json.JSONObject

/**
 * doors / cards / settings 를 SharedPreferences 에 org.json 문자열로 영속화한다.
 * (신규 Gradle 의존성 없이 안드로이드 기본 org.json 만 사용)
 */
class ServiceStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // --- 문 설정 ---

    fun loadDoors(): List<DoorConfig> {
        val raw = prefs.getString(KEY_DOORS, null) ?: return defaultDoors()
        return runCatching {
            val array = JSONArray(raw)
            val byIndex = (0 until array.length())
                .map { array.getJSONObject(it) }
                .associate { obj ->
                    val index = obj.getInt("index")
                    index to DoorConfig(
                        index = index,
                        name = obj.optString("name", ""),
                        openSeconds = obj.optInt("openSeconds", 3).coerceIn(0, 99),
                        enabled = obj.optBoolean("enabled", false)
                    )
                }
            defaultDoors().map { byIndex[it.index] ?: it }
        }.getOrElse { defaultDoors() }
    }

    fun saveDoors(doors: List<DoorConfig>) {
        val array = JSONArray()
        doors.forEach { door ->
            array.put(
                JSONObject()
                    .put("index", door.index)
                    .put("name", door.name)
                    .put("openSeconds", door.openSeconds)
                    .put("enabled", door.enabled)
            )
        }
        prefs.edit().putString(KEY_DOORS, array.toString()).apply()
    }

    // --- 카드 ---

    fun loadCards(): List<RegisteredCard> {
        val raw = prefs.getString(KEY_CARDS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                RegisteredCard(
                    key = obj.getString("key"),
                    name = obj.optString("name", ""),
                    addedAt = obj.optLong("addedAt", 0L)
                )
            }
        }.getOrElse { emptyList() }
    }

    fun saveCards(cards: List<RegisteredCard>) {
        val array = JSONArray()
        cards.forEach { card ->
            array.put(
                JSONObject()
                    .put("key", card.key)
                    .put("name", card.name)
                    .put("addedAt", card.addedAt)
            )
        }
        prefs.edit().putString(KEY_CARDS, array.toString()).apply()
    }

    // --- 얼굴 ---

    fun loadFaces(): List<RegisteredFace> {
        val raw = prefs.getString(KEY_FACES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                RegisteredFace(
                    id = obj.getString("id"),
                    name = obj.optString("name", ""),
                    addedAt = obj.optLong("addedAt", 0L),
                    embedding = obj.optJSONArray("embedding")?.let { array ->
                        (0 until array.length()).map { index -> array.optDouble(index, 0.0).toFloat() }
                    }.orEmpty()
                )
            }
        }.getOrElse { emptyList() }
    }

    fun saveFaces(faces: List<RegisteredFace>) {
        val array = JSONArray()
        faces.forEach { face ->
            array.put(
                JSONObject()
                    .put("id", face.id)
                    .put("name", face.name)
                    .put("addedAt", face.addedAt)
                    .put("embedding", JSONArray().apply { face.embedding.forEach { put(it) } })
            )
        }
        prefs.edit().putString(KEY_FACES, array.toString()).apply()
    }

    // --- 서비스 설정 ---

    fun loadSettings(): ServiceSettings {
        val raw = prefs.getString(KEY_SETTINGS, null) ?: return ServiceSettings()
        return runCatching {
            val obj = JSONObject(raw)
            val indexArray = obj.optJSONArray("autoOpenDoorIndexes") ?: JSONArray()
            val indexes = (0 until indexArray.length()).map { indexArray.getInt(it) }.toSet()
            ServiceSettings(
                autoOpenEnabled = obj.optBoolean("autoOpenEnabled", false),
                autoOpenDoorIndexes = indexes
            )
        }.getOrElse { ServiceSettings() }
    }

    fun saveSettings(settings: ServiceSettings) {
        val obj = JSONObject()
            .put("autoOpenEnabled", settings.autoOpenEnabled)
            .put(
                "autoOpenDoorIndexes",
                JSONArray().apply { settings.autoOpenDoorIndexes.sorted().forEach { put(it) } }
            )
        prefs.edit().putString(KEY_SETTINGS, obj.toString()).apply()
    }

    /** 데이터 초기화 (관리자 시스템 메뉴). */
    fun clearAll() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "access_link_service"
        private const val KEY_DOORS = "doors"
        private const val KEY_CARDS = "cards"
        private const val KEY_FACES = "faces"
        private const val KEY_SETTINGS = "settings"

        /** 문은 항상 Relay0/Relay1 두 개. 기본 이름 없음/미사용/3초. */
        fun defaultDoors(): List<DoorConfig> = listOf(
            DoorConfig(index = 0),
            DoorConfig(index = 1)
        )
    }
}
