package jinproject.aideo.setting.term

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import jinproject.aideo.design.R
import jinproject.aideo.design.component.bar.BackButtonTitleAppBar
import jinproject.aideo.design.component.layout.DefaultLayout
import jinproject.aideo.design.theme.AideoTheme

@Composable
internal fun TermScreen(
    navigatePopBackStack: () -> Unit,
) {
    DefaultLayout(
        topBar = {
            BackButtonTitleAppBar(
                onBackClick = navigatePopBackStack,
                title = stringResource(R.string.term_title)
            )
        }
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    webViewClient = WebViewClient()
                }
            },
            update = { webView ->
                webView.loadUrl("https://sites.google.com/d/1bxuuJY1HGvxCRHkpHrePsohl7zR0QGOi/p/1mGeRAVteqiyAfJb4RbkC__nf247bALc5/edit")
            }
        )
    }
}

@Composable
@Preview
private fun PreviewTermScreen() = AideoTheme {
    TermScreen {}
}