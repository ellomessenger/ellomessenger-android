/*
 * This is the source code of Ello extensions for tgnet library v. 1.1
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2024.
 */

#include "ExponentialBackoffStrategy.h"

const double ExponentialBackoffStrategy::DEFAULT_MULTIPLIER = 1.5;
const long ExponentialBackoffStrategy::DEFAULT_MAX_DELAY_MS = 16000;

ExponentialBackoffStrategy::ExponentialBackoffStrategy(long initialDelay, double multiplier, long maxDelayMs, std::unique_ptr<JitterSource> jitterSource) : initialDelay(initialDelay), multiplier(multiplier), maxDelayMs(maxDelayMs), jitterSource(std::move(jitterSource)) {}

long ExponentialBackoffStrategy::getDelayMs(int tryNumber) const {
    if (tryNumber == 0) {
        return initialDelay;
    }

    double factor = std::pow(multiplier, static_cast<double>(tryNumber));
    long nextDelay = std::min(static_cast<long>(initialDelay * factor), maxDelayMs);

    return nextDelay + jitterSource->getJitterMs();
}
