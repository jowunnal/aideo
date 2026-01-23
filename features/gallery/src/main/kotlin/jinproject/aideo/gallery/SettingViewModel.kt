package jinproject.aideo.gallery

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jinproject.aideo.core.TranslationManager
import jinproject.aideo.core.inference.SpeechRecognitionAvailableModel
import jinproject.aideo.core.inference.translation.TranslationAvailableModel
import jinproject.aideo.core.utils.LanguageCode
import jinproject.aideo.data.datasource.local.LocalSettingDataSource
import jinproject.aideo.design.R
import jinproject.aideo.design.theme.AideoColor
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
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
                speechRecognitionSelectedModel = SpeechRecognitionAvailableModel.findByName(playerSetting.speechRecognitionModel),
                translationSelectedModel = TranslationAvailableModel.findByName(playerSetting.translationModel)
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
            if (translationManager.isInitialized) {
                translationManager.release()
            }
        }
    }
}

data class SettingUiState(
    val inferenceLanguage: LanguageCode,
    val translationLanguage: LanguageCode,
    val speechRecognitionSelectedModel: SpeechRecognitionAvailableModel,
    val translationSelectedModel: TranslationAvailableModel,
) {
    val speechRecognitionSettings: ImmutableSet<ModelSettingState> = persistentSetOf(
        ModelSettingState(
            name = SpeechRecognitionAvailableModel.SenseVoice.name,
            descRes = R.string.model_sensevoice_desc,
            tagRes = R.string.model_sensevoice_tag,
            tagColor = AideoColor.amber,
        ),
        ModelSettingState(
            name = SpeechRecognitionAvailableModel.Whisper.name,
            descRes = R.string.model_whisper_desc,
            tagRes = R.string.model_whisper_tag,
            tagColor = AideoColor.blue,
        ),
    )

    val translationSettings: ImmutableSet<ModelSettingState> = persistentSetOf(
        ModelSettingState(
            name = TranslationAvailableModel.MlKit.name,
            descRes = R.string.model_mlkit_desc,
            tagRes = R.string.model_mlkit_tag,
            tagColor = AideoColor.indigo,
        ),
        ModelSettingState(
            name = TranslationAvailableModel.M2M100.name,
            descRes = R.string.model_m2m100_desc,
            tagRes = R.string.model_m2m100_tag,
            tagColor = AideoColor.emerald,
        ),
    )

    companion object {
        fun default(): SettingUiState = SettingUiState(
            inferenceLanguage = LanguageCode.findByCode(Locale.getDefault().language)
                ?: LanguageCode.Auto,
            translationLanguage = LanguageCode.findByCode(Locale.getDefault().language)
                ?: LanguageCode.Korean,
            speechRecognitionSelectedModel = SpeechRecognitionAvailableModel.SenseVoice,
            translationSelectedModel = TranslationAvailableModel.MlKit
        )
    }
}

data class ModelSettingState(
    val name: String,
    @StringRes val descRes: Int,
    @StringRes val tagRes: Int,
    val tagColor: AideoColor,
)