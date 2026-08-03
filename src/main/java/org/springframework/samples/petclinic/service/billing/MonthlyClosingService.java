package org.springframework.samples.petclinic.service.billing;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.samples.petclinic.model.Owner;
import org.springframework.samples.petclinic.model.Pet;
import org.springframework.samples.petclinic.model.Visit;
import org.springframework.samples.petclinic.service.ClinicService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Monthly closing process: charges every visit of the period, applies the holiday surcharge and
 * the configured discount rule, and produces one invoice per owner.
 */
@Service
public class MonthlyClosingService {

    private static final Logger LOG = LoggerFactory.getLogger(MonthlyClosingService.class);

    private final ClinicService clinicService;

    private final BillingRateLoader rateLoader;

    private final DiscountPluginLoader pluginLoader;

    @Autowired
    public MonthlyClosingService(ClinicService clinicService, BillingRateLoader rateLoader,
                                DiscountPluginLoader pluginLoader) {
        this.clinicService = clinicService;
        this.rateLoader = rateLoader;
        this.pluginLoader = pluginLoader;
    }

    @Transactional(readOnly = true)
    public List<Invoice> close(String period) {
        Date anyDayInMonth = parsePeriod(period);
        Date from = LegacyDateFormats.startOfMonth(anyDayInMonth);
        Date to = LegacyDateFormats.endOfMonth(anyDayInMonth);
        BillingRates rates = rateLoader.load();
        DiscountPlugin discountPlugin = pluginLoader.load();

        List<Invoice> invoices = new ArrayList<Invoice>();
        for (Owner owner : clinicService.findOwnerByLastName("")) {
            Invoice invoice = new Invoice(owner.getId(), owner.getLastName() + " " + owner.getFirstName(), from, to);
            for (Pet pet : owner.getPets()) {
                appendPetLines(invoice, pet, from, to, rates, discountPlugin);
            }
            if (!invoice.isEmpty()) {
                invoices.add(invoice);
            }
        }
        LOG.info("closing {} produced {} invoices", period, invoices.size());
        return invoices;
    }

    private void appendPetLines(Invoice invoice, Pet pet, Date from, Date to, BillingRates rates,
                                DiscountPlugin discountPlugin) {
        String petType = pet.getType() == null ? "*" : pet.getType().getName();
        long unitPrice = rates.unitPriceFor(petType);
        for (Visit visit : clinicService.findVisitsByPetId(pet.getId())) {
            if (visit.getDate() == null) {
                continue;
            }
            Date visitDate = toDate(visit.getDate());
            if (visitDate.before(from) || visitDate.after(to)) {
                continue;
            }
            Calendar visitDay = LegacyDateFormats.toCalendar(visitDate);
            long surcharge = surchargeFor(visitDay, unitPrice, rates);
            long discount = discountPlugin.discountFor(visitDay, petType, unitPrice);
            invoice.addLine(new InvoiceLine(visitDate, pet.getName(), petType, visit.getDescription(),
                unitPrice, surcharge, discount));
        }
    }

    private long surchargeFor(Calendar visitDay, long unitPrice, BillingRates rates) {
        int dayOfWeek = visitDay.get(Calendar.DAY_OF_WEEK);
        if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) {
            return Math.round(unitPrice * rates.getHolidaySurchargeRate());
        }
        return 0L;
    }

    private Date parsePeriod(String period) {
        try {
            return LegacyDateFormats.periodFormat().parse(period);
        } catch (ParseException ex) {
            throw new IllegalArgumentException("period must be formatted as "
                + LegacyDateFormats.PERIOD_PATTERN + ": " + period, ex);
        }
    }

    private Date toDate(java.time.LocalDate date) {
        Calendar calendar = Calendar.getInstance(java.util.TimeZone.getDefault());
        calendar.clear();
        calendar.set(date.getYear(), date.getMonthValue() - 1, date.getDayOfMonth());
        return calendar.getTime();
    }
}
