package jinproject.aideo.data.datasource.local

import androidx.datastore.core.DataStore
import jinproject.aideo.app.PlayerPreferences
import jinproject.aideo.data.datasource.local.datastore.serializer.PlayerPreferencesSerializer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject

class LocalSettingDataSource @Inject constructor(private val playerDataStore: DataStore<PlayerPreferences>) {

    private val data: Flow<PlayerPreferences> = playerDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(PlayerPreferencesSerializer.defaultValue)
            } else {
                throw exception
            }
        }

    fun getTotalSettings(): Flow<PlayerSetting> = data.map {
        PlayerSetting(
            inferenceLanguage = it.inferenceLanguage,
            subtitleLanguage = it.subtitleLanguage,
            videoUris = it.videosList,
            speechRecognitionModel = it.selectedSpeechRecognitionModel,
            translationModel = it.selectedTranslationModel
        )
    }

    fun getInferenceTargetLanguage(): Flow<String> = data.map { it.inferenceLanguage }

    fun getVideoUris(): Flow<List<String>> = data.map { it.videosList }

    fun getSubtitleLanguage(): Flow<String> = data.map { it.subtitleLanguage }

    fun getSelectedSpeechRecognitionModel(): Flow<String> = data.map { it.selectedSpeechRecognitionModel }

    fun getSelectedTranslationModel(): Flow<String> = data.map { it.selectedTranslationModel }

    suspend fun setInferenceTargetLanguage(language: String) {
        playerDataStore.updateData {
            it.toBuilder().setInferenceLanguage(language).build()
        }
    }

    suspend fun replaceVideoUris(uris: List<String>) {
        playerDataStore.updateData {
            it.toBuilder()
                .clearVideos()
                .addAllVideos(uris)
                .build()
        }
    }

    suspend fun setSubtitleLanguage(language: String) {
        playerDataStore.updateData {
            it.toBuilder().setSubtitleLanguage(language).build()
        }
    }

    suspend fun setSelectedSpeechRecognitionModel(model: String) {
        playerDataStore.updateData {
            it.toBuilder().setSelectedSpeechRecognitionModel(model).build()
        }
    }

    suspend fun setSelectedTranslationModel(model: String) {
        playerDataStore.updateData {
            it.toBuilder().setSelectedTranslationModel(model).build()
        }
    }
}

data class PlayerSetting(
    val inferenceLanguage: String,
    val subtitleLanguage: String,
    val videoUris: List<String>,
    val speechRecognitionModel: String,
    val translationModel: String,
)