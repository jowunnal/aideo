#ifndef AIDEO_PATH_UTILS_H
#define AIDEO_PATH_UTILS_H

namespace aideo {

    inline bool isInvalidPath(const char* path) {
        return path == nullptr || path[0] == '\0';
    }

}

#endif
