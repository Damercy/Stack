package com.stackapp.stack.offbalance
import org.junit.Assert.*
import org.junit.Test
import java.time.*
import java.util.Locale

class LocalCompetitionTimeTest {
    private fun String.normalizedSpaces()=replace('\u202f',' ').replace('\u00a0',' ')
    private val now=Instant.parse("2026-10-09T13:00:00Z")
    @Test fun indiaShowsItsLocalResetInsteadOfUtc(){val label=competitionTimeLabel(now,ZoneId.of("Asia/Kolkata"),Locale.UK);assertEquals("9 Oct 2026",label.date);assertEquals("Resets tomorrow at 05:30",label.reset)}
    @Test fun losAngelesShowsSameDayLocalReset(){assertEquals("Resets at 5:00 PM",competitionTimeLabel(now,ZoneId.of("America/Los_Angeles"),Locale.US).reset.normalizedSpaces())}
    @Test fun daylightSavingChangesUseTheActualResetInstant(){
        val summer=competitionTimeLabel(now,ZoneId.of("America/New_York"),Locale.US)
        val winter=competitionTimeLabel(Instant.parse("2026-12-09T13:00:00Z"),ZoneId.of("America/New_York"),Locale.US)
        assertEquals("Resets at 8:00 PM",summer.reset.normalizedSpaces());assertEquals("Resets at 7:00 PM",winter.reset.normalizedSpaces())
    }
    @Test fun localDatesCanDifferFromTheCompetitionDay(){val label=competitionTimeLabel(Instant.parse("2026-10-09T23:00:00Z"),ZoneId.of("Pacific/Auckland"),Locale.UK);assertEquals("10 Oct 2026",label.date);assertFalse(label.reset.contains("tomorrow"))}
}
