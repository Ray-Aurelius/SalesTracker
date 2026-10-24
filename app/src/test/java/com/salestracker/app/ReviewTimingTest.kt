package com.salestracker.app

import com.salestracker.app.data.ReviewTiming
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewTimingTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 1_800_000_000_000L
    private val firstUse = now - 10 * day

    @Test fun dueAfterAWeekAndThreeSales() = assertTrue(ReviewTiming.isDue(true, firstUse, 3, 0L, 0, now))
    @Test fun neverWhenTurnedOff() = assertFalse(ReviewTiming.isDue(false, firstUse, 50, 0L, 0, now))
    @Test fun notInTheFirstWeek() = assertFalse(ReviewTiming.isDue(true, now - 6 * day, 10, 0L, 0, now))
    @Test fun notBeforeThreeSales() = assertFalse(ReviewTiming.isDue(true, firstUse, 2, 0L, 0, now))
    @Test fun notAgainWithin120Days() = assertFalse(ReviewTiming.isDue(true, now - 400 * day, 10, now - 119 * day, 1, now))
    @Test fun againAfter120Days() = assertTrue(ReviewTiming.isDue(true, now - 400 * day, 10, now - 121 * day, 1, now))
    @Test fun atMostThreeTimes() = assertFalse(ReviewTiming.isDue(true, now - 900 * day, 10, now - 300 * day, 3, now))
    @Test fun unknownFirstUseNeverAsks() = assertFalse(ReviewTiming.isDue(true, 0L, 10, 0L, 0, now))
}
