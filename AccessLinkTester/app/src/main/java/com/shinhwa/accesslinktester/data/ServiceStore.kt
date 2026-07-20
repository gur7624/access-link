package com.shinhwa.accesslinktester.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.shinhwa.accesslinktester.model.DoorConfig
import com.shinhwa.accesslinktester.model.AccessEvent
import com.shinhwa.accesslinktester.model.AccessEventType
import com.shinhwa.accesslinktester.model.RegisteredCard
import com.shinhwa.accesslinktester.model.RegisteredFace
import com.shinhwa.accesslinktester.model.ServiceSettings
import org.json.JSONArray
import org.json.JSONObject
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * doors / cards / settings 를 SharedPreferences 에 org.json 문자열로 영속화한다.
 * (신규 Gradle 의존성 없이 안드로이드 기본 org.json 만 사용)
 */
class ServiceStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // --- 문 설정 ---

    fun loadDoors(): List<DoorConfig> {
        val raw = readValue(KEY_DOORS) ?: return defaultDoors()
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
        saveValue(KEY_DOORS, array.toString())
    }

    // --- 카드 ---

    fun loadCards(): List<RegisteredCard> {
        val raw = readValue(KEY_CARDS) ?: return emptyList()
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
        saveValue(KEY_CARDS, array.toString())
    }

    // --- 얼굴 ---

    fun loadFaces(): List<RegisteredFace> {
        val raw = readValue(KEY_FACES) ?: return emptyList()
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
        saveValue(KEY_FACES, array.toString())
    }

    // --- 서비스 설정 ---

    fun loadSettings(): ServiceSettings {
        val raw = readValue(KEY_SETTINGS) ?: return ServiceSettings()
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
        saveValue(KEY_SETTINGS, obj.toString())
    }

    // --- 출입 감사 기록 ---

    fun loadAccessEvents(): List<AccessEvent> {
        val raw = readValue(KEY_ACCESS_EVENTS) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val obj = array.getJSONObject(index)
                val type = runCatching {
                    AccessEventType.valueOf(obj.getString("type"))
                }.getOrNull() ?: return@mapNotNull null
                AccessEvent(
                    time = obj.optString("time", ""),
                    type = type,
                    personName = obj.optNullableString("personName"),
                    cardNumber = obj.optNullableString("cardNumber"),
                    message = obj.optString("message", ""),
                    detail = obj.optNullableString("detail")
                )
            }
        }.getOrElse { emptyList() }
    }

    fun saveAccessEvents(events: List<AccessEvent>) {
        val array = JSONArray()
        events.forEach { event ->
            array.put(
                JSONObject()
                    .put("time", event.time)
                    .put("type", event.type.name)
                    .put("personName", event.personName ?: JSONObject.NULL)
                    .put("cardNumber", event.cardNumber ?: JSONObject.NULL)
                    .put("message", event.message)
                    .put("detail", event.detail ?: JSONObject.NULL)
            )
        }
        saveValue(KEY_ACCESS_EVENTS, array.toString())
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
        private const val KEY_ACCESS_EVENTS = "access_events"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "access_link_service_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val ENCRYPTED_PREFIX = "v1:"
        private const val GCM_IV_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128

        /** 문은 항상 Relay0/Relay1 두 개. 기본 이름 없음/미사용/3초. */
        fun defaultDoors(): List<DoorConfig> = listOf(
            DoorConfig(index = 0),
            DoorConfig(index = 1)
        )
    }

    private fun readValue(key: String): String? {
        val raw = prefs.getString(key, null) ?: return null
        if (!raw.startsWith(ENCRYPTED_PREFIX)) {
            runCatching { saveValue(key, raw) }
            return raw
        }
        return runCatching { decrypt(raw.removePrefix(ENCRYPTED_PREFIX)) }.getOrNull()
    }

    private fun saveValue(key: String, value: String) {
        prefs.edit()
            .putString(key, ENCRYPTED_PREFIX + encrypt(value))
            .apply()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(cipher.iv.size + encrypted.size)
        cipher.iv.copyInto(combined)
        encrypted.copyInto(combined, destinationOffset = cipher.iv.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val combined = Base64.decode(value, Base64.NO_WRAP)
        require(combined.size > GCM_IV_LENGTH_BYTES)
        val iv = combined.copyOfRange(0, GCM_IV_LENGTH_BYTES)
        val encrypted = combined.copyOfRange(GCM_IV_LENGTH_BYTES, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        )
        return cipher.doFinal(encrypted).toString(Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    private fun JSONObject.optNullableString(name: String): String? {
        return if (isNull(name)) null else getString(name)
    }

}
