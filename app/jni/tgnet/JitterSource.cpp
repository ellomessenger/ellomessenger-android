/*
 * This is the source code of Ello extensions for tgnet library v. 1.1
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2024.
 */

#include "JitterSource.h"
#include <cstdlib>
#include <random>
#include <chrono>

const long JitterSource::DEFAULT_JITTER_MS = 16000;

JitterSource::JitterSource(long maxJitterMs) : maxJitterMs(maxJitterMs), randomEngine(std::random_device{}()) {}

long JitterSource::getJitterMs() const {
    std::uniform_int_distribution<long> dist(0, maxJitterMs - 1);
    return std::abs(dist(randomEngine));
}
