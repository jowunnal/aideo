package jinproject.aideo.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jinproject.aideo.core.TranslationManager
import jinproject.aideo.core.inference.SpeechRecognitionAvailableModel
import jinproject.aideo.core.inference.translation.TranslationAvailableModel
import jinproject.aideo.core.utils.LanguageCode
import jinproject.aideo.data.datasource.local.LocalSettingDataSource
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class SettingViewModel @Inject constructor(
    private val localSettingDataSource: LocalSettingDataSource,
    private val translationManager: TranslationManager,
) : ViewModel() {

    val settingUiState: StateFlow<SettingUiState> =
        localSettingDataSource.getTotalSettings().map { playerSetting ->
            SettingUiState(
                inferenceLanguage = LanguageCode.findByCode(playerSetting.inferenceLanguage)
                    ?: LanguageCode.Auto,
                translationLanguage = LanguageCode.findByCode(playerSetting.subtitleLanguage)
                    ?: LanguageCode.Korean,
                speechRecognitionModel = SpeechRecognitionAvailableModel.findByName(playerSetting.speechRecognitionModel),
                translationModel = TranslationAvailableModel.findByName(playerSetting.translationModel)
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingUiState.default()
        )

    fun updateInferenceLanguage(languageCode: LanguageCode) {
        viewModelScope.launch {
            localSettingDataSource.setInferenceTargetLanguage(languageCode.code)
        }
    }

    fun updateTranslationLanguage(languageCode: LanguageCode) {
        viewModelScope.launch {
            localSettingDataSource.setSubtitleLanguage(languageCode.code)
        }
    }

    fun updateSpeechRecognitionModel(model: SpeechRecognitionAvailableModel) {
        viewModelScope.launch {
            localSettingDataSource.setSelectedSpeechRecognitionModel(model.name)
        }
    }

    fun updateTranslationModel(model: TranslationAvailableModel) {
        viewModelScope.launch {
            localSettingDataSource.setSelectedTranslationModel(model.name)
            if(translationManager.isInitialized) {
                translationManager.release()
            }
        }
    }
}

data class SettingUiState(
    val inferenceLanguage: LanguageCode,
    val translationLanguage: LanguageCode,
    val speechRecognitionModel: SpeechRecognitionAvailableModel,
    val translationModel: TranslationAvailableModel,
) {
    companion object {
        fun default(): SettingUiState = SettingUiState(
            inferenceLanguage = LanguageCode.findByCode(Locale.getDefault().language)
                ?: LanguageCode.Auto,
            translationLanguage = LanguageCode.findByCode(Locale.getDefault().language)
                ?: LanguageCode.Korean,
            speechRecognitionModel = SpeechRecognitionAvailableModel.SenseVoice,
            translationModel = TranslationAvailableModel.MlKit
        )
    }
}