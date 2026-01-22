package jinproject.aideo.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jinproject.aideo.core.inference.SpeechRecognitionAvailableModel
import jinproject.aideo.core.inference.translation.TranslationAvailableModel
import jinproject.aideo.core.utils.LanguageCode
import jinproject.aideo.design.component.DropDownMenuCustom
import jinproject.aideo.design.component.VerticalSpacer
import jinproject.aideo.design.component.bar.BackButtonTitleAppBar
import jinproject.aideo.design.component.text.DescriptionLargeText
import jinproject.aideo.design.component.text.DescriptionMediumText
import jinproject.aideo.design.component.text.DescriptionSmallText
import jinproject.aideo.design.theme.AideoColor
import jinproject.aideo.design.theme.AideoTheme
import jinproject.aideo.design.utils.tu

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
    updateInferenceLanguageCode: (LanguageCode) -> Unit,
    updateTranslationLanguageCode: (LanguageCode) -> Unit,
    updateSpeechRecognitionModel: (SpeechRecognitionAvailableModel) -> Unit,
    updateTranslationModel: (TranslationAvailableModel) -> Unit,
    navigatePopBackStack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        BackButtonTitleAppBar(
            title = "설정 화면",
            onBackClick = navigatePopBackStack,
            backgroundColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        )

        VerticalSpacer(20.dp)

        LanguageSetting(
            title = "추론 언어",
            description = "음성 인식에 사용할 언어를 선택해 주세요.",
            items = LanguageCode.getLanguageCodesByAvailableModel(settingUiState.speechRecognitionModel)
                .map { it.name },
            selectedText = settingUiState.inferenceLanguage.name,
            onClickItem = { item ->
                updateInferenceLanguageCode(LanguageCode.findByName(item))
            },
        )

        VerticalSpacer(20.dp)

        LanguageSetting(
            title = "번역 언어",
            description = "자막으로 번역할 언어를 선택해 주세요.",
            items = LanguageCode.getLanguageCodesByAvailableModel(settingUiState.speechRecognitionModel)
                .map { it.name },
            selectedText = settingUiState.translationLanguage.name,
            onClickItem = { item ->
                updateTranslationLanguageCode(LanguageCode.findByName(item))
            }
        )

        VerticalSpacer(20.dp)

        LanguageSetting(
            title = "추론 모델",
            description = "추론에 사용될 모델을 선택해 주세요.",
            items = SpeechRecognitionAvailableModel.entries.map { it.name },
            selectedText = settingUiState.speechRecognitionModel.name,
            onClickItem = { item ->
                updateSpeechRecognitionModel(SpeechRecognitionAvailableModel.findByName(item))
            }
        )

        VerticalSpacer(20.dp)

        LanguageSetting(
            title = "번역 모델",
            description = "번역에 사용될 모델을 선택해 주세요.",
            items = TranslationAvailableModel.entries.map { it.name },
            selectedText = settingUiState.translationModel.name,
            onClickItem = { item ->
                updateTranslationModel(TranslationAvailableModel.findByName(item))
            }
        )

        VerticalSpacer(20.dp)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    RoundedCornerShape(10.dp)
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    RoundedCornerShape(10.dp)
                )
                .padding(horizontal = 12.dp, vertical = 16.dp),
        ) {
            DescriptionLargeText(
                text = "현재 설정:",
                color = AideoColor.deep_primary.color,
            )
            VerticalSpacer(8.dp)
            DescriptionMediumText(
                text = """- 추론 언어: ${settingUiState.inferenceLanguage}
                    |- 번역 언어: ${settingUiState.translationLanguage}
                    |- 추론 모델: ${settingUiState.speechRecognitionModel}
                    |- 번역 모델: ${settingUiState.translationModel}
                """.trimMargin(),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.tu)
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