package org.springframework.samples.petclinic.service.billing;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Pins the fee master (BR-17) as it is read through JAXB (IF-03, T-04).
 */
public class BillingRateLoaderTests {

    private final BillingRates rates = new BillingRateLoader().load();

    @Test
    public void readsUnitPricePerPetType() {
        assertEquals(4500L, rates.unitPriceFor("dog"));
        assertEquals(4000L, rates.unitPriceFor("cat"));
        assertEquals(5200L, rates.unitPriceFor("lizard"));
        assertEquals(3600L, rates.unitPriceFor("bird"));
        assertEquals(3000L, rates.unitPriceFor("hamster"));
    }

    @Test
    public void matchesPetTypeIgnoringCase() {
        assertEquals(4500L, rates.unitPriceFor("DOG"));
    }

    @Test
    public void fallsBackToTheDefaultUnitPrice() {
        assertEquals(3800L, rates.unitPriceFor("turtle"));
    }

    @Test
    public void readsClosingParameters() {
        assertEquals("JPY", rates.getCurrency());
        assertEquals(25, rates.getClosingDay());
        assertEquals(0.25d, rates.getHolidaySurchargeRate(), 0.0001d);
    }
}
