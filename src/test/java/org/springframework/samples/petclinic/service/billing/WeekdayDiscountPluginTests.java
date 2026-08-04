package org.springframework.samples.petclinic.service.billing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Calendar;
import java.util.GregorianCalendar;

import org.junit.Test;

/**
 * Characterization tests for the reflectively-loaded discount plugin (BR-5, BR-6, BR-7) and for the
 * fact that {@link DiscountPluginLoader} instantiates {@link WeekdayDiscountPlugin} through its
 * private constructor (P-REF-2). Weekday base discount = 5%, lizard campaign adds 10% (=15% total),
 * weekend discount = 0.
 */
public class WeekdayDiscountPluginTests {

    private final DiscountPlugin plugin = new DiscountPluginLoader().load();

    private static Calendar day(int year, int month0, int dayOfMonth) {
        Calendar c = new GregorianCalendar();
        c.clear();
        c.set(year, month0, dayOfMonth);
        return c;
    }

    @Test
    public void loaderReturnsWeekdayDiscountPluginViaPrivateConstructor() {
        assertThat(plugin).isInstanceOf(WeekdayDiscountPlugin.class);
    }

    @Test
    public void weekdayBaseDiscountIsFivePercentRounded() {
        // 2013-01-04 is a Friday. round(4500 * 0.05) = 225.
        assertThat(plugin.discountFor(day(2013, Calendar.JANUARY, 4), "dog", 4500L)).isEqualTo(225L);
    }

    @Test
    public void weekdayLizardCampaignAddsTenPercent() {
        // Friday lizard: round(5200 * 0.15) = 780.
        assertThat(plugin.discountFor(day(2013, Calendar.JANUARY, 4), "lizard", 5200L)).isEqualTo(780L);
        assertThat(plugin.discountFor(day(2013, Calendar.JANUARY, 4), "LIZARD", 5200L)).isEqualTo(780L);
    }

    @Test
    public void snakeGetsBaseDiscountOnly() {
        // Friday snake: round(5200 * 0.05) = 260 (campaign is lizard-only).
        assertThat(plugin.discountFor(day(2013, Calendar.JANUARY, 4), "snake", 5200L)).isEqualTo(260L);
    }

    @Test
    public void weekendGetsNoDiscount() {
        assertThat(plugin.discountFor(day(2013, Calendar.JANUARY, 5), "dog", 4500L)).isEqualTo(0L);
        assertThat(plugin.discountFor(day(2013, Calendar.JANUARY, 6), "lizard", 5200L)).isEqualTo(0L);
    }
}
