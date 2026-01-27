package jinproject.aideo.gallery.setting.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jinproject.aideo.design.R
import jinproject.aideo.design.component.VerticalSpacer
import jinproject.aideo.design.component.button.clickableAvoidingDuplication
import jinproject.aideo.design.component.text.DescriptionLargeText
import jinproject.aideo.design.component.text.DescriptionMediumText
import jinproject.aideo.design.component.text.DescriptionSmallText
import jinproject.aideo.design.theme.AideoColor
import jinproject.aideo.design.theme.AideoTheme
import jinproject.aideo.gallery.setting.ModelSettingState
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf

@Composable
internal fun ModelSetting(
    header: String,
    description: String,
    currentClickedModel: String,
    models: ImmutableSet<ModelSettingState>,
    onClickModel: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .shadow(1.dp, RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.background, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 16.dp),
    ) {
        DescriptionLargeText(text = header)
        VerticalSpacer(8.dp)
        models.forEach {
            SingleModelSetting(
                model = it,
                currentClickedModel = currentClickedModel,
                onClickModel = onClickModel,
            )
            if (models.last() != it)
                VerticalSpacer(12.dp)
        }
        VerticalSpacer(8.dp)
        DescriptionSmallText(
            text = description,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
    }
}

@Composable
fun SingleModelSetting(
    model: ModelSettingState,
    currentClickedModel: String,
    onClickModel: (String) -> Unit,
) {
    val clickedOrNotColor by animateColorAsState(
        if (currentClickedModel == model.name)
            MaterialTheme.colorScheme.primary
        else
            MaterialTheme.colorScheme.outline.copy(0.2f)
    )
    val backgroundColor by animateColorAsState(
        if (currentClickedModel == model.name)
            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        else
            MaterialTheme.colorScheme.background
    )

    Column(
        modifier = Modifier
            .background(backgroundColor.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
            .drawBehind {
                drawRect(
                    color = clickedOrNotColor,
                    style = Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = PathEffect.cornerPathEffect(10.dp.toPx())
                    )
                )
            }
            .clickableAvoidingDuplication {
                onClickModel(model.name)
            }
            .padding(12.dp)
    ) {
        DescriptionMediumText(
            text = model.name
        )
        VerticalSpacer(8.dp)
        DescriptionSmallText(
            text = stringResource(model.descRes),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
        )
        VerticalSpacer(8.dp)
        DescriptionSmallText(
            text = stringResource(model.tagRes),
            color = model.tagColor.color,
            modifier = Modifier
                .background(
                    model.tagColor.color.copy(0.2f),
                    shape = RoundedCornerShape(10.dp)
                )
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewModelSetting() = AideoTheme {
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        ModelSetting(
            header = stringResource(R.string.settings_inference_model),
            description = stringResource(R.string.settings_inference_model_desc),
            currentClickedModel = "SenseVoice",
            models = persistentSetOf(
                ModelSettingState(
                    name = "SenseVoice",
                    descRes = R.string.model_sensevoice_desc,
                    tagRes = R.string.model_sensevoice_tag,
                    tagColor = AideoColor.amber_400
                ),
                ModelSettingState(
                    name = "Whisper",
                    descRes = R.string.model_whisper_desc,
                    tagRes = R.string.model_whisper_tag,
                    tagColor = AideoColor.blue
                )
            ),
            onClickModel = {},
        )
    }
}