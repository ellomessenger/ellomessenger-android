package org.telegram.ui.profile.subscriptions_channels

import kotlin.random.Random

data class Totals(val available: Int = Random.nextInt(1000), val earnedLastMonth: Int = Random.nextInt(1000), val totalEarned: Int = Random.nextInt(1000))
