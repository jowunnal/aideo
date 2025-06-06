package jinproject.aideo.design.utils

fun <T> T.runIf(predicate: Boolean, block: T.() -> T): T = if(predicate) block() else this