package jinproject.aideo.gallery.setting

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.play.core.aipacks.model.AiPackStatus
import jinproject.aideo.core.BillingModule
import jinproject.aideo.core.inference.AiModelConfig
import jinproject.aideo.core.inference.SpeechRecognitionAvailableModel
import jinproject.aideo.core.inference.translation.TranslationAvailableModel
import jinproject.aideo.core.utils.LanguageCode
import jinproject.aideo.core.utils.LocalBillingModule
import jinproject.aideo.core.utils.getAiPackManager
import jinproject.aideo.core.utils.getAiPackStates
import jinproject.aideo.core.utils.getPackAssetPath
import jinproject.aideo.core.utils.getPackStatus
import jinproject.aideo.design.R
import jinproject.aideo.design.component.DropDownMenuCustom
import jinproject.aideo.design.component.TextDialog
import jinproject.aideo.design.component.VerticalSpacer
import jinproject.aideo.design.component.bar.BackButtonTitleAppBar
import jinproject.aideo.design.component.button.clickableAvoidingDuplication
import jinproject.aideo.design.component.getShownDialogState
import jinproject.aideo.design.component.rememberDialogState
import jinproject.aideo.design.component.text.DescriptionLargeText
import jinproject.aideo.design.component.text.DescriptionSmallText
import jinproject.aideo.design.theme.AideoColor
import jinproject.aideo.design.theme.AideoTheme
import jinproject.aideo.gallery.setting.component.ModelSetting
import jinproject.aideo.gallery.setting.component.SubscriptionManagementSetting

@Composable
fun SettingScreen(
    viewModel: SettingViewModel = hiltViewModel(),
    navigatePopBackStack: () -> Unit,
    navigateToSubscriptionManagement: () -> Unit,
    navigateToSubscription: () -> Unit,
    navigateToTerm: () -> Unit,
) {
    val settingUiState by viewModel.settingUiState.collectAsStateWithLifecycle()
    val billingModule = LocalBillingModule.current
    val hasSubscription by produceState(initialValue = false, billingModule) {
        value = billingModule.isProductPurchased(BillingModule.Product.REMOVE_AD)
    }

    SettingScreen(
        settingUiState = settingUiState,
        hasSubscription = hasSubscription,
        updateInferenceLanguageCode = viewModel::updateInferenceLanguage,
        updateTranslationLanguageCode = viewModel::updateTranslationLanguage,
        updateSpeechRecognitionModel = viewModel::updateSpeechRecognitionModel,
        updateTranslationModel = viewModel::updateTranslationModel,
        navigatePopBackStack = navigatePopBackStack,
        navigateToSubscriptionManagement = navigateToSubscriptionManagement,
        navigateToSubscription = navigateToSubscription,
        navigateToTerm = navigateToTerm,
    )

}

@Composable
internal fun SettingScreen(
    settingUiState: SettingUiState,
    hasSubscription: Boolean,
    context: Context = LocalContext.current,
    updateInferenceLanguageCode: (LanguageCode) -> Unit,
    updateTranslationLanguageCode: (LanguageCode) -> Unit,
    updateSpeechRecognitionModel: (SpeechRecognitionAvailableModel) -> Unit,
    updateTranslationModel: (TranslationAvailableModel) -> Unit,
    navigatePopBackStack: () -> Unit,
    navigateToSubscriptionManagement: () -> Unit,
    navigateToSubscription: () -> Unit,
    navigateToTerm: () -> Unit,
) {
    var dialogState by rememberDialogState()

    TextDialog(dialogState = dialogState) {
        dialogState.changeVisibility(false)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 20.dp),
    ) {
        BackButtonTitleAppBar(
            title = stringResource(R.string.settings_title),
            onBackClick = navigatePopBackStack,
            backgroundColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        )

        VerticalSpacer(20.dp)

        LanguageSetting(
            title = stringResource(R.string.settings_inference_language),
            description = stringResource(R.string.settings_inference_language_desc),
            items = LanguageCode.getLanguageCodesByAvailableModel(settingUiState.speechRecognitionSelectedModel)
                .map { it.name },
            selectedText = settingUiState.inferenceLanguage.name,
            onClickItem = { item ->
                updateInferenceLanguageCode(LanguageCode.findByName(item))
            },
        )

        VerticalSpacer(20.dp)

        LanguageSetting(
            title = stringResource(R.string.settings_translation_language),
            description = stringResource(R.string.settings_translation_language_desc),
            items = LanguageCode.getLanguageCodesByAvailableModel(settingUiState.speechRecognitionSelectedModel)
                .map { it.name },
            selectedText = settingUiState.translationLanguage.name,
            onClickItem = { item ->
                updateTranslationLanguageCode(LanguageCode.findByName(item))
            }
        )

        VerticalSpacer(20.dp)

        ModelSetting(
            header = stringResource(R.string.settings_inference_model),
            description = stringResource(R.string.settings_inference_model_desc),
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
                                        header = context.getString(R.string.dialog_download_required_header),
                                        content = context.getString(R.string.dialog_download_whisper_content),
                                        positiveMessage = context.getString(R.string.dialog_download_positive),
                                        negativeMessage = context.getString(R.string.dialog_download_negative),
                                    ).getShownDialogState(
                                        onPositiveCallback = {
                                            context.getAiPackManager()
                                                .fetch(listOf(AiModelConfig.SPEECH_WHISPER_PACK))
                                                .addOnCompleteListener {
                                                    if (it.isSuccessful && context.getPackAssetPath(
                                                            AiModelConfig.SPEECH_WHISPER_PACK
                                                        ) != null
                                                    )
                                                        updateSpeechRecognitionModel(
                                                            SpeechRecognitionAvailableModel.findByName(
                                                                item
                                                            )
                                                        )
                                                }
                                        }
                                    )
                                }

                                else -> {}
                            }
                        }
                } else
                    updateSpeechRecognitionModel(SpeechRecognitionAvailableModel.findByName(item))
            }
        )

        VerticalSpacer(20.dp)

        ModelSetting(
            header = stringResource(R.string.settings_translation_model),
            description = stringResource(R.string.settings_translation_model_desc),
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
                                        header = context.getString(R.string.dialog_download_required_header),
                                        content = context.getString(R.string.dialog_download_required_content),
                                        positiveMessage = context.getString(R.string.dialog_download_positive),
                                        negativeMessage = context.getString(R.string.dialog_download_negative),
                                    ).getShownDialogState(
                                        onPositiveCallback = {
                                            context.getAiPackManager()
                                                .fetch(listOf(AiModelConfig.TRANSLATION_BASE_PACK))
                                                .addOnCompleteListener {
                                                    if (it.isSuccessful && context.getPackAssetPath(
                                                            AiModelConfig.TRANSLATION_BASE_PACK
                                                        ) != null
                                                    )
                                                        updateTranslationModel(
                                                            TranslationAvailableModel.findByName(
                                                                item
                                                            )
                                                        )
                                                }
                                        }
                                    )
                                }

                                else -> {}
                            }
                        }
                } else
                    updateTranslationModel(TranslationAvailableModel.findByName(item))
            }
        )

        VerticalSpacer(20.dp)

        SubscriptionManagementSetting(
            hasSubscription = hasSubscription,
            navigateToSubscriptionManagement = navigateToSubscriptionManagement,
            navigateToSubscription = navigateToSubscription,
        )

        VerticalSpacer(20.dp)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .shadow(1.dp, RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.background, RoundedCornerShape(10.dp))
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DescriptionSmallText(
                text = stringResource(R.string.term_title),
            )
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_arrow_right_small),
                contentDescription = "Navigate to term of service screen",
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.clickableAvoidingDuplication(onClick = navigateToTerm)
            )
        }
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
            iconTail = R.drawable.ic_arrow_down_outlined,
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
        hasSubscription = true,
        updateInferenceLanguageCode = {},
        updateTranslationLanguageCode = {},
        updateSpeechRecognitionModel = {},
        updateTranslationModel = {},
        navigatePopBackStack = {},
        navigateToSubscriptionManagement = {},
        navigateToSubscription = {},
        navigateToTerm = {},
    )
}