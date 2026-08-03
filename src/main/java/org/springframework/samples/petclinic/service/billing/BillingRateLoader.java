package org.springframework.samples.petclinic.service.billing;

import java.io.InputStream;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Loads the fee master from the classpath using JAXB.
 */
@Component
public class BillingRateLoader {

    private static final Logger LOG = LoggerFactory.getLogger(BillingRateLoader.class);

    private static final String RATES_LOCATION = "/billing/rates.xml";

    private BillingRates cached;

    public synchronized BillingRates load() {
        if (cached != null) {
            return cached;
        }
        InputStream in = getClass().getResourceAsStream(RATES_LOCATION);
        if (in == null) {
            throw new IllegalStateException("fee master not found: " + RATES_LOCATION);
        }
        try {
            JAXBContext context = JAXBContext.newInstance(BillingRates.class);
            Unmarshaller unmarshaller = context.createUnmarshaller();
            cached = (BillingRates) unmarshaller.unmarshal(in);
            LOG.info("loaded {} rate entries, closing day {}", cached.getRates().size(), cached.getClosingDay());
            return cached;
        } catch (Exception ex) {
            throw new IllegalStateException("failed to load fee master", ex);
        } finally {
            try {
                in.close();
            } catch (Exception ignored) {
                LOG.debug("could not close fee master stream", ignored);
            }
        }
    }
}
