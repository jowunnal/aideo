package jinproject.aideo.core.utils

import android.content.Context

fun Context.getPackageContext(): Context = createPackageContext(packageName, 0)