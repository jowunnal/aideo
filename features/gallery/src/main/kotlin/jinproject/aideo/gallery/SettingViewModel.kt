package jinproject.aideo.gallery

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jinproject.aideo.core.inference.AvailableModel
import jinproject.aideo.core.utils.LanguageCode
import jinproject.aideo.data.datasource.local.LocalPlayerDataSource
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class SettingViewModel @Inject constructor(
    private val localPlayerDataSource: LocalPlayerDataSource,
) : ViewModel() {

    val settingUiState: StateFlow<SettingUiState> =
        localPlayerDataSource.getTotalSettings().map { playerSetting ->
            SettingUiState(
                inferenceLanguage = LanguageCode.findByCode(playerSetting.inferenceLanguage) ?: LanguageCode.Auto,
                translationLanguage = LanguageCode.findByCode(playerSetting.subtitleLanguage) ?: LanguageCode.Korean,
                selectedModel = AvailableModel.findByName(playerSetting.selectedModel)
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingUiState.default()
        )

    fun updateInferenceLanguage(languageCode: LanguageCode) {
        viewModelScope.launch {
            localPlayerDataSource.setInferenceTargetLanguage(languageCode.code)
        }
    }

    fun updateTranslationLanguage(languageCode: LanguageCode) {
        viewModelScope.launch {
            localPlayerDataSource.setSubtitleLanguage(languageCode.code)
        }
    }

    fun updateSelectedModel(availableModel: AvailableModel) {
        viewModelScope.launch {
            localPlayerDataSource.setSelectedModel(availableModel.name)
        }
    }
}

data class SettingUiState(
    val inferenceLanguage: LanguageCode,
    val translationLanguage: LanguageCode,
    val selectedModel: AvailableModel,
) {
    companion object {
        fun default(): SettingUiState = SettingUiState(
            inferenceLanguage = LanguageCode.findByCode(Locale.getDefault().language) ?: LanguageCode.Auto,
            translationLanguage = LanguageCode.findByCode(Locale.getDefault().language) ?: LanguageCode.Korean,
            selectedModel = AvailableModel.SenseVoice,
        )
    }
}