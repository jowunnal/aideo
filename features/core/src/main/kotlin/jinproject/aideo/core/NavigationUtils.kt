package jinproject.aideo.core

fun String.parseUri() = this.replace("/", "*")

fun String.toOriginUri() = this.replace("*", "/")