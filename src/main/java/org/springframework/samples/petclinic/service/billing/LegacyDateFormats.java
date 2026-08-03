package org.springframework.samples.petclinic.service.billing;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;


/**
 * Date handling shared by the closing process. Formats follow the paper forms used by the clinic,
 * so they are evaluated in the platform default time zone and locale.
 */
public final class LegacyDateFormats {


    public static final String PERIOD_PATTERN = "yyyy/MM";

    public static final String IMPORTED_VISIT_PATTERN = "yy/MM/dd";

    public static final String REPORT_PATTERN = "yyyy年MM月dd日(E)";

    private LegacyDateFormats() {
    }

    public static SimpleDateFormat periodFormat() {
        return new SimpleDateFormat(PERIOD_PATTERN);
    }

    public static SimpleDateFormat reportFormat() {
        return new SimpleDateFormat(REPORT_PATTERN, Locale.getDefault());
    }

    /**
     * Formatter for visit dates imported from the old accounting system, where the year has two
     * digits. The century window starts at the configured year.
     */
    public static SimpleDateFormat importedVisitFormat(int twoDigitYearStart) {
        SimpleDateFormat format = new SimpleDateFormat(IMPORTED_VISIT_PATTERN);
        Calendar start = Calendar.getInstance(TimeZone.getDefault());
        start.clear();
        start.set(Calendar.YEAR, twoDigitYearStart);
        format.set2DigitYearStart(start.getTime());
        return format;
    }

    public static Date startOfMonth(Date anyDayInMonth) {
        Calendar calendar = Calendar.getInstance(TimeZone.getDefault());
        calendar.setTime(anyDayInMonth);
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        clearTime(calendar);
        return calendar.getTime();
    }

    public static Date endOfMonth(Date anyDayInMonth) {
        Calendar calendar = Calendar.getInstance(TimeZone.getDefault());
        calendar.setTime(anyDayInMonth);
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH));
        clearTime(calendar);
        return calendar.getTime();
    }

    public static Calendar toCalendar(Date date) {
        Calendar calendar = Calendar.getInstance(TimeZone.getDefault());
        calendar.setTime(date);
        clearTime(calendar);
        return calendar;
    }

    private static void clearTime(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }
}
