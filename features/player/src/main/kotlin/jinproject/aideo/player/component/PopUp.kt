package jinproject.aideo.player.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import jinproject.aideo.core.utils.LanguageCode
import jinproject.aideo.design.component.PopUp
import jinproject.aideo.design.component.PopUpInfo
import jinproject.aideo.design.component.button.clickableAvoidingDuplication
import jinproject.aideo.design.component.text.DescriptionMediumText
import jinproject.aideo.player.PlayerUiState

@Composable
internal fun PlayerPopUp(
    popUpInfo: PopUpInfo,
    uiState: PlayerUiState,
    updateLanguageCode: (LanguageCode) -> Unit,
) {
    PopUp(
        popUpInfo = popUpInfo
    ) {
        Column(
            modifier = Modifier
                .shadow(
                    1.dp,
                    RoundedCornerShape(20.dp)
                )
                .background(
                    MaterialTheme.colorScheme.background,
                    RoundedCornerShape(20.dp)
                )
        ) {
            LanguageCode.entries.filter { it != LanguageCode.Auto }.toTypedArray()
                .forEach { language ->
                    DescriptionMediumText(
                        text = language.name,
                        modifier = Modifier
                            .clickableAvoidingDuplication {
                                updateLanguageCode(language)
                            }
                            .padding(vertical = 8.dp, horizontal = 16.dp)
                            .graphicsLayer {
                                alpha =
                                    if (language.code == uiState.currentLanguage) 1f else 0.5f
                            },
                    )
                }
        }
    }
}