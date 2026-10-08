package com.salestracker.app.data

import java.time.LocalDate
import java.time.ZoneId

/**
 * Made-up example data so a new user can explore a "lived-in" app before entering their own.
 * Every id is returned so the sample can be removed later without touching anything the user added.
 * Sample appointments have no reminders, so nothing rings for fake data.
 */
object SampleData {
    data class Sample(
        val clients: List<Client>,
        val sales: List<Sale>,
        val appointments: List<Appointment>,
        val goals: List<Goal>,
        val tasks: List<Task>,
        val highlightDays: Map<Long, HighlightColor>,
    ) {
        val ids: Set<Long> get() = (clients.map { it.id } + sales.map { it.id } + appointments.map { it.id } + goals.map { it.id } + tasks.map { it.id }).toSet()
    }

    fun create(newId: () -> Long, commissionPercent: Double, today: LocalDate = LocalDate.now()): Sample {
        val zone = ZoneId.systemDefault()
        val names = listOf(
            Triple("Alex", "Morgan", ClientStage.WON), Triple("Jordan", "Lee", ClientStage.NEGOTIATING),
            Triple("Sam", "Patel", ClientStage.PROPOSAL), Triple("Taylor", "Brooks", ClientStage.CONTACTED),
            Triple("Casey", "Rivera", ClientStage.LEAD), Triple("Morgan", "Chen", ClientStage.WON),
        )
        val jobs = listOf("Dentist", "Restaurant owner", "Contractor", "Teacher", "Homeowner", "Nurse")
        val clients = names.mapIndexed { i, (first, last, stage) ->
            Client(
                id = newId(), firstName = first, lastName = last,
                phone = "555-01${10 + i}", email = "${first.lowercase()}@example.com",
                reference = "WO-${1040 + i}", occupation = jobs[i % jobs.size], stage = stage,
            )
        }
        // Three weeks of sales: roughly 6 in 10 closed, some with upsells, varied amounts and times.
        val amounts = listOf(1200.0, 850.0, 2400.0, 640.0, 1800.0, 975.0, 3100.0, 720.0, 1450.0, 2200.0, 560.0, 1300.0, 1650.0, 900.0, 2750.0)
        val sales = amounts.mapIndexed { i, amount ->
            val day = today.minusDays((i * 3 / 2).toLong())
            val closed = i % 5 != 1 && i % 7 != 3
            val upOffered = i % 2 == 0
            val upAccepted = closed && upOffered && i % 4 == 0
            Sale(
                id = newId(),
                clientId = clients[i % clients.size].id,
                timestamp = day.atTime(9 + (i % 8), (i * 7) % 60).atZone(zone).toInstant().toEpochMilli(),
                closed = closed,
                upsellOffered = upOffered,
                upsellAccepted = upAccepted,
                amount = amount,
                upsellAmount = if (upAccepted) amount * 0.2 else 0.0,
                durationSeconds = (12 + (i * 5) % 30) * 60L,
                notes = "",
                commissionPercent = commissionPercent,
            )
        }
        val appointments = listOf(
            Triple(0L, 14 * 60, 1), Triple(1L, 10 * 60 + 30, 2), Triple(2L, 15 * 60, 3), Triple(4L, 11 * 60, 4),
        ).map { (plusDays, minute, clientIdx) ->
            Appointment(
                id = newId(), title = "${clients[clientIdx].firstName} ${clients[clientIdx].lastName}",
                epochDay = today.plusDays(plusDays).toEpochDay(), minuteOfDay = minute,
                clientId = clients[clientIdx].id, notes = "", reminderMinutes = null,
            )
        }
        val goals = listOf(
            Goal(newId(), "Monthly commission", GoalScope.PERSONAL, GoalMetric.COMMISSION, GoalPeriod.MONTH, 3000.0),
            Goal(newId(), "Quarterly team revenue", GoalScope.COMPANY, GoalMetric.REVENUE, GoalPeriod.QUARTER, 60000.0),
        )
        // A day's to-dos: client follow-ups up top, smaller jobs below, one daily habit. No reminders on fake data.
        val todayDay = today.toEpochDay()
        val tasks = listOf(
            Task(newId(), "Follow up with ${clients[1].firstName} on the revised quote", todayDay, important = true, clientId = clients[1].id),
            Task(newId(), "Send proposal to ${clients[2].firstName} ${clients[2].lastName}", todayDay, important = true, clientId = clients[2].id),
            Task(newId(), "Make 10 prospecting calls", todayDay - 7, repeat = TaskRepeat.WEEKDAYS),
            Task(newId(), "Thank-you card for ${clients[0].firstName}", todayDay, clientId = clients[0].id, doneDays = setOf(todayDay)),
            Task(newId(), "Update pipeline notes", todayDay),
        )
        return Sample(clients, sales, appointments, goals, tasks, mapOf(today.plusDays(2).toEpochDay() to HighlightColor.RED))
    }
}
