package jinproject.aideo.design.component.lottie

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.compose.LottieAnimation
import jinproject.aideo.design.R
import jinproject.aideo.design.theme.AideoTheme

@Composable
fun PreviewLottieAnimation(
    modifier: Modifier = Modifier,
    composition: LottieComposition?,
    progress: Float,
) {
    if (LocalInspectionMode.current)
        Image(
            painter = painterResource(id = R.drawable.test),
            contentDescription = null,
            modifier = modifier
        )
    else
        LottieAnimation(
            composition = composition,
            progress = { progress },
            modifier = modifier,
        )
}

@Composable
@Preview
private fun PreviewPreviewLottieAnimation() = AideoTheme {
    PreviewLottieAnimation(
        modifier = Modifier.size(120.dp),
        composition = null,
        progress = 0f,
    )
}