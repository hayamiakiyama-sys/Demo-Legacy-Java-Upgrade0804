package org.springframework.samples.petclinic.service.billing;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Fee master loaded from billing/rates.xml.
 */
@XmlRootElement(name = "billing-rates")
@XmlAccessorType(XmlAccessType.FIELD)
public class BillingRates {

    @XmlAttribute(name = "currency")
    private String currency = "JPY";

    @XmlElement(name = "rate")
    private List<Rate> rates = new ArrayList<Rate>();

    @XmlElement(name = "holiday-surcharge-rate")
    private double holidaySurchargeRate = 0.25d;

    @XmlElement(name = "closing-day")
    private int closingDay = 25;

    public String getCurrency() {
        return currency;
    }

    public List<Rate> getRates() {
        return rates;
    }

    public double getHolidaySurchargeRate() {
        return holidaySurchargeRate;
    }

    public int getClosingDay() {
        return closingDay;
    }

    public long unitPriceFor(String petTypeName) {
        for (Rate rate : rates) {
            if (rate.getPetType().equalsIgnoreCase(petTypeName)) {
                return rate.getUnitPrice();
            }
        }
        return defaultUnitPrice();
    }

    private long defaultUnitPrice() {
        for (Rate rate : rates) {
            if ("*".equals(rate.getPetType())) {
                return rate.getUnitPrice();
            }
        }
        return 3000L;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Rate {

        @XmlAttribute(name = "pet-type")
        private String petType;

        @XmlAttribute(name = "unit-price")
        private long unitPrice;

        public String getPetType() {
            return petType;
        }

        public long getUnitPrice() {
            return unitPrice;
        }
    }
}
