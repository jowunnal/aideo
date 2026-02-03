#ifndef AIDEO_LOGGING_H
#define AIDEO_LOGGING_H

#include <android/log.h>

#define AIDEO_LOGI(tag, ...) __android_log_print(ANDROID_LOG_INFO, tag, __VA_ARGS__)
#define AIDEO_LOGW(tag, ...) __android_log_print(ANDROID_LOG_WARN, tag, __VA_ARGS__)
#define AIDEO_LOGE(tag, ...) __android_log_print(ANDROID_LOG_ERROR, tag, __VA_ARGS__)

#endif
