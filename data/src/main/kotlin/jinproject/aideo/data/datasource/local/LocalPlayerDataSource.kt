package jinproject.aideo.data.datasource.local

import androidx.datastore.core.DataStore
import jinproject.aideo.app.PlayerPreferences
import jinproject.aideo.data.datasource.local.datastore.serializer.PlayerPreferencesSerializer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

class LocalPlayerDataSource(private val playerDataStore: DataStore<PlayerPreferences>) {

    private val data: Flow<PlayerPreferences> = playerDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(PlayerPreferencesSerializer.defaultValue)
            } else {
                throw exception
            }
        }

    fun getLanguageSetting(): Flow<String> = data.map { it.language }

    suspend fun setLanguageSetting(language: String) {
        playerDataStore.updateData {
            it.toBuilder().setLanguage(language).build()
        }
    }
}