/*
 * This is the source code of Ello extensions for tgnet library v. 1.1
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2024.
 */

#ifndef ELLOAPP_ANDROID_EXPONENTIALBACKOFFSTRATEGY_H
#define ELLOAPP_ANDROID_EXPONENTIALBACKOFFSTRATEGY_H

#include "JitterSource.h"
#include <cmath>
#include <algorithm>

class ExponentialBackoffStrategy {
public:
    explicit ExponentialBackoffStrategy(long initialDelay, double multiplier = DEFAULT_MULTIPLIER, long maxDelayMs = DEFAULT_MAX_DELAY_MS, std::unique_ptr<JitterSource> jitterSource = std::make_unique<JitterSource>());

    [[nodiscard]] long getDelayMs(int tryNumber) const;

    static const double DEFAULT_MULTIPLIER;
    static const long DEFAULT_MAX_DELAY_MS;

private:
    long initialDelay;
    double multiplier;
    long maxDelayMs;
    std::unique_ptr<JitterSource> jitterSource;
};

#endif //ELLOAPP_ANDROID_EXPONENTIALBACKOFFSTRATEGY_H
