package com.salestracker.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Everything the app knows about one client's history, worked out from the sales logged with them
 * (each one timed on the stopwatch or entered by hand), their appointments and their tasks.
 *
 * A "visit" is one logged sale record with this client: a meeting, call or appointment that was timed
 * or written down, whether or not it closed.
 */
data class ClientMetrics(
    val visits: Int,
    val totalSeconds: Long,
    val closed: Int,
    val revenue: Double,
    val largestSale: Double,
    val commission: Double,
    val upsellsAccepted: Int,
    val upsellRevenue: Double,
    /** Days (epoch day) of the first and latest visit; null with no visits. */
    val firstVisitDay: Long?,
    val lastVisitDay: Long?,
    val upcomingAppointments: Int,
    val openTasks: Int,
) {
    val averageSecondsPerVisit: Long get() = if (visits == 0) 0 else totalSeconds / visits
    val closeRate: Double get() = if (visits == 0) 0.0 else closed.toDouble() / visits
    /** What the client spends per closed sale. */
    val averageSale: Double get() = if (closed == 0) 0.0 else revenue / closed

    /** The typical gap between visits, in days (how often they buy or meet); needs two visits on different days. */
    val averageDaysBetweenVisits: Double?
        get() {
            val first = firstVisitDay ?: return null
            val last = lastVisitDay ?: return null
            if (visits < 2 || last == first) return null
            return (last - first).toDouble() / (visits - 1)
        }

    companion object {
        fun of(
            client: Client,
            data: AppData,
            today: LocalDate = LocalDate.now(),
            zone: ZoneId = ZoneId.systemDefault(),
            nowMillis: Long = System.currentTimeMillis(),
        ): ClientMetrics {
            val sales = data.sales.filter { it.clientId == client.id }
            val days = sales.map { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate().toEpochDay() }
            val todayDay = today.toEpochDay()
            return ClientMetrics(
                visits = sales.size,
                totalSeconds = sales.sumOf { it.durationSeconds },
                closed = sales.count { it.closed },
                revenue = sales.sumOf { it.revenue },
                largestSale = sales.filter { it.closed }.maxOfOrNull { it.revenue } ?: 0.0,
                commission = sales.sumOf { it.commission(data.defaultCommissionPercent) },
                upsellsAccepted = sales.count { it.closed && it.upsellAccepted },
                upsellRevenue = sales.filter { it.closed && it.upsellAccepted }.sumOf { it.upsellAmount },
                firstVisitDay = days.minOrNull(),
                lastVisitDay = days.maxOrNull(),
                upcomingAppointments = data.appointments.count { it.clientId == client.id && it.startMillis(zone) >= nowMillis },
                openTasks = data.tasks.count { t ->
                    t.clientId == client.id && (if (t.repeat == TaskRepeat.NONE) t.doneDays.isEmpty() else !t.isDoneOn(todayDay))
                },
            )
        }
    }
}
