package com.salestracker.app.data

/**
 * Finds clients by what a salesperson remembers: part of a first or last name, a phone number
 * (punctuation ignored, so "5550110" finds "555-0110"), an email, a job number or an occupation.
 * Every word typed has to match something; names that start with the search come first.
 */
object ClientSearch {
    fun filter(clients: List<Client>, query: String): List<Client> {
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val sorted = clients.sortedBy { it.label().lowercase() }
        if (words.isEmpty()) return sorted
        return sorted
            .filter { c -> words.all { w -> matches(c, w) } }
            .sortedByDescending { c -> c.firstName.lowercase().startsWith(words.first()) || c.lastName.lowercase().startsWith(words.first()) }
    }

    private fun matches(c: Client, word: String): Boolean {
        val digits = word.filter { it.isDigit() }
        if (digits.length >= 2 && digits.length >= word.count { it.isLetterOrDigit() } &&
            c.phone.filter { it.isDigit() }.contains(digits)) return true
        return listOf(c.firstName, c.lastName, c.email, c.reference, c.occupation, c.phone)
            .any { it.lowercase().contains(word) }
    }
}
