package com.inkvpn.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.inkvpn.app.core.AppSettings
import com.inkvpn.app.core.DeepLink
import com.inkvpn.app.core.Subscription
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "inkvpn")

/** Persists subscriptions and the selected server in DataStore (as JSON). */
class Repository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val fetcher = SubscriptionFetcher(context)

    private val SUBS = stringPreferencesKey("subscriptions")
    private val SELECTED = stringPreferencesKey("selected_server")
    private val SETTINGS = stringPreferencesKey("app_settings")

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        prefs[SETTINGS]?.let {
            runCatching { json.decodeFromString(AppSettings.serializer(), it) }.getOrNull()
        } ?: AppSettings()
    }

    suspend fun currentSettings(): AppSettings = settings.first()

    suspend fun saveSettings(s: AppSettings) {
        context.dataStore.edit { it[SETTINGS] = json.encodeToString(AppSettings.serializer(), s) }
    }

    val subscriptions: Flow<List<Subscription>> = context.dataStore.data.map { prefs ->
        prefs[SUBS]?.let {
            runCatching { json.decodeFromString(ListSerializer(Subscription.serializer()), it) }.getOrDefault(emptyList())
        } ?: emptyList()
    }

    val selectedServerId: Flow<String?> = context.dataStore.data.map { it[SELECTED] }

    suspend fun selectedServerId(): String? = selectedServerId.first()

    suspend fun setSelectedServer(id: String) {
        context.dataStore.edit { it[SELECTED] = id }
    }

    private suspend fun saveAll(list: List<Subscription>) {
        context.dataStore.edit {
            it[SUBS] = json.encodeToString(ListSerializer(Subscription.serializer()), list)
        }
    }

    suspend fun current(): List<Subscription> = subscriptions.first()

    /** Add or update a subscription by URL/deep-link. Fetches and parses it. Deduplicates by rawUrl. */
    suspend fun addOrUpdateSubscription(name: String?, urlInput: String, existingId: String? = null): Subscription {
        val rawUrl = DeepLink.extractSubscriptionUrl(urlInput)
        val parsed = fetcher.fetch(rawUrl)
        val title = name?.takeIf { it.isNotBlank() } ?: parsed.info.profileTitle ?: "InkVPN"
        val list = current().toMutableList()
        val existingByUrl = list.firstOrNull { it.rawUrl == rawUrl }
        val resolvedId = existingId ?: existingByUrl?.id ?: UUID.randomUUID().toString()
        val sub = Subscription(
            id = resolvedId,
            name = title,
            url = DeepLink.toStoredForm(rawUrl),
            rawUrl = rawUrl,
            servers = parsed.servers,
            info = parsed.info,
            lastUpdated = System.currentTimeMillis(),
        )
        val idx = list.indexOfFirst { it.id == sub.id }
        if (idx >= 0) list[idx] = sub.copy(pinned = list[idx].pinned) else list.add(sub)
        saveAll(list)
        return sub
    }

    suspend fun refresh(id: String): Subscription? {
        val sub = current().firstOrNull { it.id == id } ?: return null
        return addOrUpdateSubscription(sub.name, sub.rawUrl, existingId = id)
    }

    suspend fun delete(id: String) {
        saveAll(current().filterNot { it.id == id })
    }

    suspend fun togglePin(id: String) {
        saveAll(current().map { if (it.id == id) it.copy(pinned = !it.pinned) else it })
    }
}
