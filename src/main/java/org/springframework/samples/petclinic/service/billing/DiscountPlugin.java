package org.springframework.samples.petclinic.service.billing;

import java.util.Calendar;

/**
 * Pluggable discount rule. Implementations are instantiated reflectively by
 * {@link DiscountPluginLoader} from the class name configured in billing.properties.
 */
public interface DiscountPlugin {

    long discountFor(Calendar visitDay, String petType, long unitPrice);
}
