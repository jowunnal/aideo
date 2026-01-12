package jinproject.aideo.core.utils

import android.content.Context
import android.content.res.AssetManager

fun Context.getApplicationAssets(): AssetManager = createPackageContext("jinproject.aideo.app", 0).assets