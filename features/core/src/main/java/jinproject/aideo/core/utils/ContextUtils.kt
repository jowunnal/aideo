package jinproject.aideo.core.utils

import android.content.Context
import android.content.res.AssetManager

fun Context.getAiPackAssets(): AssetManager = createPackageContext("jinproject.aideo.app", 0).assets