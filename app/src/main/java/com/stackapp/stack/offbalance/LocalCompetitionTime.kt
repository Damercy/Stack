package com.stackapp.stack.offbalance

import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

data class CompetitionTimeLabel(val date:String,val reset:String)
fun competitionTimeLabel(now:Instant=Instant.now(),zone:ZoneId=ZoneId.systemDefault(),locale:Locale=Locale.getDefault()):CompetitionTimeLabel {
    val local=now.atZone(zone)
    val reset=now.atZone(ZoneOffset.UTC).toLocalDate().plusDays(1).atStartOfDay(ZoneOffset.UTC).withZoneSameInstant(zone)
    val time=DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(reset)
    val date=DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(local)
    return CompetitionTimeLabel(date,"Resets ${if(reset.toLocalDate()>local.toLocalDate())"tomorrow " else ""}at $time")
}
