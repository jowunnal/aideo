package jinproject.aideo.app

import java.util.Calendar
import java.util.TimeZone

fun main() {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul"))

    cal.apply {
        set(Calendar.YEAR, 2025)
        set(Calendar.MONTH, 9 - 1)
        set(Calendar.DAY_OF_MONTH, 13)
        set(Calendar.HOUR_OF_DAY, 20)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
    }


    val simpleDateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
    simpleDateFormat.timeZone = TimeZone.getTimeZone("America/Toronto")
    println("toronto time : ${simpleDateFormat.format(cal.time)}")
    //09시


    simpleDateFormat.timeZone = TimeZone.getTimeZone("Europe/Paris")
    println("paris time: ${simpleDateFormat.format(cal.time)}")
    //15시
}