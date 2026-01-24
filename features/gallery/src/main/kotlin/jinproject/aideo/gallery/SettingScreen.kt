package jinproject.aideo.gallery

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.play.core.aipacks.model.AiPackStatus
import jinproject.aideo.core.inference.AiModelConfig
import jinproject.aideo.core.inference.SpeechRecognitionAvailableModel
import jinproject.aideo.core.inference.translation.TranslationAvailableModel
import jinproject.aideo.core.utils.LanguageCode
import jinproject.aideo.core.utils.LocalShowSnackBar
import jinproject.aideo.core.utils.getAiPackManager
import jinproject.aideo.core.utils.getAiPackStates
import jinproject.aideo.core.utils.getPackStatus
import jinproject.aideo.design.component.DropDownMenuCustom
import jinproject.aideo.design.component.TextDialog
import jinproject.aideo.design.component.VerticalSpacer
import jinproject.aideo.design.component.bar.BackButtonTitleAppBar
import jinproject.aideo.design.component.getShownDialogState
import jinproject.aideo.design.component.rememberDialogState
import jinproject.aideo.design.component.text.DescriptionLargeText
import jinproject.aideo.design.component.text.DescriptionSmallText
import jinproject.aideo.design.theme.AideoColor
import jinproject.aideo.design.theme.AideoTheme
import jinproject.aideo.gallery.component.ModelSetting

@Composable
fun SettingScreen(
    viewModel: SettingViewModel = hiltViewModel(),
    navigatePopBackStack: () -> Unit,
) {
    val settingUiState by viewModel.settingUiState.collectAsStateWithLifecycle()

    SettingScreen(
        settingUiState = settingUiState,
        updateInferenceLanguageCode = viewModel::updateInferenceLanguage,
        updateTranslationLanguageCode = viewModel::updateTranslationLanguage,
        updateSpeechRecognitionModel = viewModel::updateSpeechRecognitionModel,
        updateTranslationModel = viewModel::updateTranslationModel,
        navigatePopBackStack = navigatePopBackStack,
    )

}

@Composable
internal fun SettingScreen(
    settingUiState: SettingUiState,
    context: Context = LocalContext.current,
    updateInferenceLanguageCode: (LanguageCode) -> Unit,
    updateTranslationLanguageCode: (LanguageCode) -> Unit,
    updateSpeechRecognitionModel: (SpeechRecognitionAvailableModel) -> Unit,
    updateTranslationModel: (TranslationAvailableModel) -> Unit,
    navigatePopBackStack: () -> Unit,
) {
    var dialogState by rememberDialogState()
    val showSnackBar = LocalShowSnackBar.current

    TextDialog(dialogState = dialogState) {
        dialogState.changeVisibility(false)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        BackButtonTitleAppBar(
            title = stringResource(jinproject.aideo.design.R.string.settings_title),
            onBackClick = navigatePopBackStack,
            backgroundColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        )

        VerticalSpacer(20.dp)

        LanguageSetting(
            title = stringResource(jinproject.aideo.design.R.string.settings_inference_language),
            description = stringResource(jinproject.aideo.design.R.string.settings_inference_language_desc),
            items = LanguageCode.getLanguageCodesByAvailableModel(settingUiState.speechRecognitionSelectedModel)
                .map { it.name },
            selectedText = settingUiState.inferenceLanguage.name,
            onClickItem = { item ->
                updateInferenceLanguageCode(LanguageCode.findByName(item))
            },
        )

        VerticalSpacer(20.dp)

        LanguageSetting(
            title = stringResource(jinproject.aideo.design.R.string.settings_translation_language),
            description = stringResource(jinproject.aideo.design.R.string.settings_translation_language_desc),
            items = LanguageCode.getLanguageCodesByAvailableModel(settingUiState.speechRecognitionSelectedModel)
                .map { it.name },
            selectedText = settingUiState.translationLanguage.name,
            onClickItem = { item ->
                updateTranslationLanguageCode(LanguageCode.findByName(item))
            }
        )

        VerticalSpacer(20.dp)

        ModelSetting(
            header = stringResource(jinproject.aideo.design.R.string.settings_inference_model),
            description = stringResource(jinproject.aideo.design.R.string.settings_inference_model_desc),
            currentClickedModel = settingUiState.speechRecognitionSelectedModel.name,
            models = settingUiState.speechRecognitionSettings,
            onClickModel = { item ->
                if (SpeechRecognitionAvailableModel.Whisper.name.equals(
                        other = item,
                        ignoreCase = true
                    ) && context.getAiPackManager()
                        .getPackLocation(AiModelConfig.SPEECH_WHISPER_PACK) == null
                ) {
                    context
                        .getAiPackStates(AiModelConfig.SPEECH_WHISPER_PACK)
                        .addOnCompleteListener { task ->
                            when (task.getPackStatus(AiModelConfig.SPEECH_WHISPER_PACK)) {
                                AiPackStatus.NOT_INSTALLED, AiPackStatus.FAILED, AiPackStatus.CANCELED, AiPackStatus.PENDING -> {
                                    dialogState = dialogState.copy(
                                        header = context.getString(jinproject.aideo.design.R.string.dialog_download_required_header),
                                        content = "다운로드를 위해 추가 저장 공간(약 300MB)이 필요해요.",
                                        positiveMessage = context.getString(jinproject.aideo.design.R.string.dialog_download_positive),
                                        negativeMessage = context.getString(jinproject.aideo.design.R.string.dialog_download_negative),
                                    ).getShownDialogState(
                                        onPositiveCallback = {
                                            context.getAiPackManager()
                                                .fetch(listOf(AiModelConfig.SPEECH_WHISPER_PACK))
                                                .addOnCompleteListener {
                                                    if (it.isSuccessful)
                                                        updateSpeechRecognitionModel(
                                                            SpeechRecognitionAvailableModel.findByName(
                                                                item
                                                            )
                                                        )
                                                }

                                            context.startForegroundService(
                                                Intent(
                                                    context,
                                                    Class.forName("jinproject.aideo.app.PlayAIService")
                                                )
                                            )
                                        }
                                    )
                                }

                                else -> {}
                            }
                        }
                }
            }
        )

        VerticalSpacer(20.dp)

        ModelSetting(
            header = stringResource(jinproject.aideo.design.R.string.settings_translation_model),
            description = stringResource(jinproject.aideo.design.R.string.settings_translation_model_desc),
            currentClickedModel = settingUiState.translationSelectedModel.name,
            models = settingUiState.translationSettings,
            onClickModel = { item ->
                if (TranslationAvailableModel.M2M100.name.equals(
                        item,
                        ignoreCase = true
                    ) && context.getAiPackManager()
                        .getPackLocation(AiModelConfig.TRANSLATION_BASE_PACK) == null
                ) {
                    context.getAiPackStates(AiModelConfig.TRANSLATION_BASE_PACK)
                        .addOnCompleteListener { task ->
                            when (task.getPackStatus(AiModelConfig.TRANSLATION_BASE_PACK)) {
                                AiPackStatus.NOT_INSTALLED, AiPackStatus.FAILED, AiPackStatus.CANCELED, AiPackStatus.PENDING -> {
                                    dialogState = dialogState.copy(
                                        header = context.getString(jinproject.aideo.design.R.string.dialog_download_required_header),
                                        content = context.getString(jinproject.aideo.design.R.string.dialog_download_required_content),
                                        positiveMessage = context.getString(jinproject.aideo.design.R.string.dialog_download_positive),
                                        negativeMessage = context.getString(jinproject.aideo.design.R.string.dialog_download_negative),
                                    ).getShownDialogState(
                                        onPositiveCallback = {
                                            context.getAiPackManager()
                                                .fetch(listOf(AiModelConfig.TRANSLATION_BASE_PACK))
                                                .addOnCompleteListener {
                                                    if (it.isSuccessful)
                                                        updateTranslationModel(
                                                            TranslationAvailableModel.findByName(
                                                                item
                                                            )
                                                        )
                                                }

                                            context.startForegroundService(
                                                Intent(
                                                    context,
                                                    Class.forName("jinproject.aideo.app.PlayAIService")
                                                )
                                            )
                                        }
                                    )
                                }

                                else -> {}
                            }
                        }
                }
            }
        )

        VerticalSpacer(20.dp)
    }
}

@Composable
private fun LanguageSetting(
    title: String,
    description: String,
    items: List<String>,
    selectedText: String,
    onClickItem: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .shadow(1.dp, RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.background, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 16.dp),
    ) {
        DescriptionLargeText(text = title)
        VerticalSpacer(8.dp)
        DropDownMenuCustom(
            items = items,
            selectedText = selectedText,
            onClickItem = onClickItem,
            modifier = Modifier
                .fillMaxWidth()
                .background(AideoColor.grey_300.color.copy(0.3f)),
            iconTail = jinproject.aideo.design.R.drawable.ic_arrow_down_outlined,
        )
        VerticalSpacer(8.dp)
        DescriptionSmallText(
            text = description,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
    }
}

@Preview
@Composable
private fun PreviewSettingScreen(
    @PreviewParameter(SettingUiStatePreviewParameter::class)
    settingUiState: SettingUiState
) = AideoTheme {
    SettingScreen(
        settingUiState = settingUiState,
        updateInferenceLanguageCode = {},
        updateTranslationLanguageCode = {},
        updateSpeechRecognitionModel = {},
        updateTranslationModel = {},
        navigatePopBackStack = {},
    )
}