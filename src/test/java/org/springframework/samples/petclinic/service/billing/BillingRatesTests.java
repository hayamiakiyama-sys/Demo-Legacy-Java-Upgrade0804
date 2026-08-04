package org.springframework.samples.petclinic.service.billing;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;

import org.junit.Test;

/**
 * Characterization tests for the fee master ({@link BillingRates}) as loaded from the real
 * {@code /billing/rates.xml}. These pin the currently shipped prices and the fallback rules
 * (BR-1, BR-2). Treat the current values as correct; a change here means the fee master or the
 * lookup logic moved.
 */
public class BillingRatesTests {

    private final BillingRates rates = new BillingRateLoader().load();

    @Test
    public void shippedRatesXmlHasExpectedUnitPrices() {
        assertThat(rates.unitPriceFor("dog")).isEqualTo(4500L);
        assertThat(rates.unitPriceFor("cat")).isEqualTo(4000L);
        assertThat(rates.unitPriceFor("lizard")).isEqualTo(5200L);
        assertThat(rates.unitPriceFor("snake")).isEqualTo(5200L);
        assertThat(rates.unitPriceFor("bird")).isEqualTo(3600L);
        assertThat(rates.unitPriceFor("hamster")).isEqualTo(3000L);
    }

    @Test
    public void lookupIsCaseInsensitive() {
        assertThat(rates.unitPriceFor("DOG")).isEqualTo(4500L);
        assertThat(rates.unitPriceFor("Lizard")).isEqualTo(5200L);
    }

    @Test
    public void unknownTypeFallsBackToWildcardRate() {
        // rates.xml defines a "*" wildcard at 3800; unknown types resolve to it.
        assertThat(rates.unitPriceFor("elephant")).isEqualTo(3800L);
        assertThat(rates.unitPriceFor("*")).isEqualTo(3800L);
    }

    @Test
    public void configuredScalarsMatchShippedFile() {
        assertThat(rates.getCurrency()).isEqualTo("JPY");
        assertThat(rates.getHolidaySurchargeRate()).isEqualTo(0.25d);
        assertThat(rates.getClosingDay()).isEqualTo(25);
    }

    /**
     * When no wildcard "*" rate is present, {@link BillingRates#unitPriceFor} falls back to the
     * hard-coded 3000L (BR-2). Pinned here because this default is invisible from rates.xml alone.
     */
    @Test
    public void hardcodedDefaultUsedWhenNoWildcardConfigured() throws Exception {
        String xml = "<billing-rates currency=\"JPY\">"
            + "<rate pet-type=\"dog\" unit-price=\"4500\"/>"
            + "<holiday-surcharge-rate>0.25</holiday-surcharge-rate>"
            + "<closing-day>25</closing-day>"
            + "</billing-rates>";
        JAXBContext ctx = JAXBContext.newInstance(BillingRates.class);
        Unmarshaller unmarshaller = ctx.createUnmarshaller();
        BillingRates noWildcard = (BillingRates) unmarshaller.unmarshal(
            new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

        assertThat(noWildcard.unitPriceFor("dog")).isEqualTo(4500L);
        assertThat(noWildcard.unitPriceFor("cat")).isEqualTo(3000L);
    }
}
