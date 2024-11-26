/*
 * This is the source code of Ello extensions for tgnet library v. 1.1
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2024.
 */

#ifndef ELLOAPP_ANDROID_JITTERSOURCE_H
#define ELLOAPP_ANDROID_JITTERSOURCE_H

#include <random>

class JitterSource {
public:
    explicit JitterSource(long maxJitterMs = DEFAULT_JITTER_MS);

    long getJitterMs() const;

    static const long DEFAULT_JITTER_MS;

private:
    long maxJitterMs;
    mutable std::mt19937 randomEngine;
};

#endif //ELLOAPP_ANDROID_JITTERSOURCE_H
