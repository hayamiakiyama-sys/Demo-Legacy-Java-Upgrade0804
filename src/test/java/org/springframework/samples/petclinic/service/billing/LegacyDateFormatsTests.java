package org.springframework.samples.petclinic.service.billing;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import org.junit.Test;

/**
 * Characterization tests for {@link LegacyDateFormats}. Pins the month-window helpers, the
 * report/period patterns, and the two-digit-year formatter. The two-digit-year formatter pins its
 * internal {@code defaultCenturyStart} field by reflection (JEP 403 hazard on Java 21); on Java 8
 * that reflection succeeds, so these assertions capture the reflective path.
 */
public class LegacyDateFormatsTests {

    private static Date date(int y, int m0, int d) {
        Calendar c = new GregorianCalendar();
        c.clear();
        c.set(y, m0, d);
        return c.getTime();
    }

    private static Calendar cal(Date date) {
        Calendar c = new GregorianCalendar();
        c.setTime(date);
        return c;
    }

    @Test
    public void startOfMonthIsFirstDayAtMidnight() {
        Calendar c = cal(LegacyDateFormats.startOfMonth(date(2013, Calendar.JANUARY, 15)));
        assertThat(c.get(Calendar.YEAR)).isEqualTo(2013);
        assertThat(c.get(Calendar.MONTH)).isEqualTo(Calendar.JANUARY);
        assertThat(c.get(Calendar.DAY_OF_MONTH)).isEqualTo(1);
        assertThat(c.get(Calendar.HOUR_OF_DAY)).isEqualTo(0);
        assertThat(c.get(Calendar.MINUTE)).isEqualTo(0);
        assertThat(c.get(Calendar.SECOND)).isEqualTo(0);
        assertThat(c.get(Calendar.MILLISECOND)).isEqualTo(0);
    }

    @Test
    public void endOfMonthIsLastDayOfMonth() {
        assertThat(cal(LegacyDateFormats.endOfMonth(date(2013, Calendar.JANUARY, 15)))
            .get(Calendar.DAY_OF_MONTH)).isEqualTo(31);
        assertThat(cal(LegacyDateFormats.endOfMonth(date(2013, Calendar.FEBRUARY, 10)))
            .get(Calendar.DAY_OF_MONTH)).isEqualTo(28);
        assertThat(cal(LegacyDateFormats.endOfMonth(date(2012, Calendar.FEBRUARY, 10)))
            .get(Calendar.DAY_OF_MONTH)).isEqualTo(29); // leap year
    }

    @Test
    public void toCalendarClearsTimeOfDay() {
        Calendar c = LegacyDateFormats.toCalendar(date(2013, Calendar.MARCH, 9));
        assertThat(c.get(Calendar.HOUR_OF_DAY)).isEqualTo(0);
        assertThat(c.get(Calendar.MINUTE)).isEqualTo(0);
    }

    /**
     * DISCREPANCY (OQ-15): {@code importedVisitFormat} sets the private {@code defaultCenturyStart}
     * field by reflection but never refreshes the derived {@code defaultCenturyStartYear} pivot, so
     * the effective century window is not the intended one. On Java 8 "79" parses to 1979 (not the
     * 2079 a real 1980 pivot would give). This is the actual current behavior; the reflection also
     * fails outright under JEP 403 on Java 21, where the public-API fallback yields 2079 instead.
     */
    @Test
    public void importedVisitFormatCenturyWindowReflectsStalePivot() throws Exception {
        SimpleDateFormat fmt = LegacyDateFormats.importedVisitFormat(1980);
        assertThat(cal(fmt.parse("13/01/05")).get(Calendar.YEAR)).isEqualTo(2013);
        assertThat(cal(fmt.parse("80/06/15")).get(Calendar.YEAR)).isEqualTo(1980);
        assertThat(cal(fmt.parse("79/12/31")).get(Calendar.YEAR)).isEqualTo(1979); // intent was 2079
        assertThat(cal(fmt.parse("00/01/01")).get(Calendar.YEAR)).isEqualTo(2000);
    }

    @Test
    public void reportFormatFollowsJapaneseCalendarLayout() {
        // Pattern is "yyyy年MM月dd日(E)"; the day-of-week text depends on the default locale.
        String formatted = LegacyDateFormats.reportFormat().format(date(2013, Calendar.JANUARY, 4));
        assertThat(formatted).startsWith("2013\u5e7401\u670804\u65e5("); // 2013年01月04日(
        assertThat(formatted).endsWith(")");
    }

    @Test
    public void patternsAreTheExpectedConstants() {
        assertThat(LegacyDateFormats.PERIOD_PATTERN).isEqualTo("yyyy/MM");
        assertThat(LegacyDateFormats.IMPORTED_VISIT_PATTERN).isEqualTo("yy/MM/dd");
        assertThat(LegacyDateFormats.REPORT_PATTERN).isEqualTo("yyyy\u5e74MM\u6708dd\u65e5(E)");
    }
}
