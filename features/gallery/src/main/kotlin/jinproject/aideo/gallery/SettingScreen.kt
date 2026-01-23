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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jinproject.aideo.core.SnackBarMessage
import jinproject.aideo.core.inference.ModelConfig
import jinproject.aideo.core.inference.SpeechRecognitionAvailableModel
import jinproject.aideo.core.inference.translation.TranslationAvailableModel
import jinproject.aideo.core.utils.LanguageCode
import jinproject.aideo.core.utils.LocalShowSnackBar
import jinproject.aideo.core.utils.getAiPackManager
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
    val dialogState by rememberDialogState()
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
                updateSpeechRecognitionModel(SpeechRecognitionAvailableModel.findByName(item))
            }
        )

        VerticalSpacer(20.dp)

        ModelSetting(
            header = stringResource(jinproject.aideo.design.R.string.settings_translation_model),
            description = stringResource(jinproject.aideo.design.R.string.settings_translation_model_desc),
            currentClickedModel = settingUiState.translationSelectedModel.name,
            models = settingUiState.translationSettings,
            onClickModel = { item ->
                updateTranslationModel(TranslationAvailableModel.findByName(item))

                if (item == TranslationAvailableModel.M2M100.name)
                    dialogState.copy(
                        header = context.getString(jinproject.aideo.design.R.string.dialog_download_required_header),
                        content = context.getString(jinproject.aideo.design.R.string.dialog_download_required_content),
                        positiveMessage = context.getString(jinproject.aideo.design.R.string.dialog_download_positive),
                        negativeMessage = context.getString(jinproject.aideo.design.R.string.dialog_download_negative),
                    ).getShownDialogState(
                        onPositiveCallback = {
                            val aiPackManager = context.getAiPackManager()

                            if (aiPackManager.getPackStates(listOf(ModelConfig.SPEECH_AI_PACK)).isComplete) {
                                aiPackManager.fetch(listOf(ModelConfig.Translation_AI_PACK))
                                context.startForegroundService(
                                    Intent(
                                        context,
                                        Class.forName("jinproject.aideo.app.PlayAIService")
                                    )
                                )
                            } else
                                showSnackBar.invoke(
                                    SnackBarMessage(
                                        headerMessage = context.getString(jinproject.aideo.design.R.string.download_ai_model_in_progress),
                                        contentMessage = context.getString(jinproject.aideo.design.R.string.download_ai_model_please_wait)
                                    )
                                )
                        },
                        onNegativeCallback = {
                            updateTranslationModel(TranslationAvailableModel.MlKit)
                        }
                    )
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