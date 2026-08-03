package org.springframework.samples.petclinic.service.billing;

import java.util.Calendar;

/**
 * Weekday morning discount. Instantiated reflectively, so the constructor is not public.
 */
public class WeekdayDiscountPlugin implements DiscountPlugin {

    private static final double WEEKDAY_DISCOUNT = 0.05d;

    private static final double LIZARD_CAMPAIGN_DISCOUNT = 0.1d;

    private WeekdayDiscountPlugin() {
    }

    @Override
    public long discountFor(Calendar visitDay, String petType, long unitPrice) {
        int dayOfWeek = visitDay.get(Calendar.DAY_OF_WEEK);
        boolean weekend = dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY;
        if (weekend) {
            return 0L;
        }
        double rate = WEEKDAY_DISCOUNT;
        if ("lizard".equalsIgnoreCase(petType)) {
            rate += LIZARD_CAMPAIGN_DISCOUNT;
        }
        return Math.round(unitPrice * rate);
    }
}
