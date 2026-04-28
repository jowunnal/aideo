#include "translator.h"

Translator::~Translator() = default;

void Translator::release() {
    isLoaded_ = false;
}
