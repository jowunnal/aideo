package jinproject.aideo.core.utils

fun String.parseUri() = this.replace("/", "*")

fun String.toOriginUri() = this.replace("*", "/")