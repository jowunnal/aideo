package jinproject.aideo.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jinproject.aideo.core.inference.AvailableModel
import jinproject.aideo.core.utils.LanguageCode
import jinproject.aideo.design.component.DropDownMenuCustom
import jinproject.aideo.design.component.HorizontalSpacer
import jinproject.aideo.design.component.HorizontalWeightSpacer
import jinproject.aideo.design.component.VerticalSpacer
import jinproject.aideo.design.component.bar.BackButtonTitleAppBar
import jinproject.aideo.design.component.text.DescriptionMediumText
import jinproject.aideo.design.theme.AideoTheme

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
        updateModel = viewModel::updateSelectedModel,
        navigatePopBackStack = navigatePopBackStack,
    )

}

@Composable
internal fun SettingScreen(
    settingUiState: SettingUiState,
    updateInferenceLanguageCode: (LanguageCode) -> Unit,
    updateTranslationLanguageCode: (LanguageCode) -> Unit,
    updateModel: (AvailableModel) -> Unit,
    navigatePopBackStack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        BackButtonTitleAppBar(
            title = "설정 화면",
            onBackClick = navigatePopBackStack,
        )

        VerticalSpacer(20.dp)

        Row(
            modifier = Modifier.fillMaxWidth().wrapContentWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DescriptionMediumText(text = "추론 타겟 언어")
            HorizontalSpacer(24.dp)
            DropDownMenuCustom(
                items = LanguageCode.entries.map { it.name },
                selectedText = settingUiState.inferenceLanguage.name,
                modifier = Modifier,
                onClickItem = { item ->
                    updateInferenceLanguageCode(LanguageCode.findByName(item))
                },
            )
        }

        VerticalSpacer(50.dp)

        Row(
            modifier = Modifier.fillMaxWidth().wrapContentWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DescriptionMediumText(text = "번역 타겟 언어")
            HorizontalSpacer(24.dp)
            DropDownMenuCustom(
                items = LanguageCode.entries.filter { it != LanguageCode.Auto }.map { it.name },
                selectedText = settingUiState.translationLanguage.name,
                onClickItem = { item ->
                    updateTranslationLanguageCode(LanguageCode.findByName(item))
                }
            )
        }

        VerticalSpacer(50.dp)

        Row(
            modifier = Modifier.fillMaxWidth().wrapContentWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DescriptionMediumText(text = "모델 선택")
            HorizontalSpacer(24.dp)
            DropDownMenuCustom(
                items = AvailableModel.entries.map { it.name },
                selectedText = settingUiState.selectedModel.name,
                onClickItem = { item ->
                    updateModel(AvailableModel.findByName(item))
                }
            )
        }

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
        updateModel = {},
        navigatePopBackStack = {},
    )
}