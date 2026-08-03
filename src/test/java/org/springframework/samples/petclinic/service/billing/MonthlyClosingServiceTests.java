package org.springframework.samples.petclinic.service.billing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.samples.petclinic.model.Owner;
import org.springframework.samples.petclinic.model.Pet;
import org.springframework.samples.petclinic.model.PetType;
import org.springframework.samples.petclinic.model.Visit;
import org.springframework.samples.petclinic.service.ClinicService;

/**
 * Pins the monthly closing behavior: period boundaries (BR-16), unit prices (BR-17),
 * holiday surcharge (BR-18), discounts (BR-19), totals (BR-20) and empty invoices (BR-21).
 * The closing is evaluated in the platform default time zone, so the test fixes it (T-10).
 */
public class MonthlyClosingServiceTests {

    private static final int CAT_PET_ID = 7;

    private TimeZone originalTimeZone;

    private Locale originalLocale;

    private ClinicService clinicService;

    private MonthlyClosingService service;

    @Before
    public void setUp() {
        originalTimeZone = TimeZone.getDefault();
        originalLocale = Locale.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
        Locale.setDefault(Locale.JAPAN);
        clinicService = mock(ClinicService.class);
        service = new MonthlyClosingService(clinicService, new BillingRateLoader(), new DiscountPluginLoader());
    }

    @After
    public void tearDown() {
        TimeZone.setDefault(originalTimeZone);
        Locale.setDefault(originalLocale);
    }

    @Test
    public void chargesWeekdayVisitWithDiscount() {
        // 2013/01/02 is a Wednesday: cat 4000 - 5% = 3800
        givenCatVisits(LocalDate.of(2013, 1, 2));

        Invoice invoice = onlyInvoice("2013/01");
        InvoiceLine line = invoice.getLines().get(0);

        assertEquals(4000L, line.getUnitPrice());
        assertEquals(0L, line.getSurcharge());
        assertEquals(200L, line.getDiscount());
        assertEquals(3800L, line.getAmount());
    }

    @Test
    public void chargesWeekendVisitWithHolidaySurchargeAndNoDiscount() {
        // 2013/01/05 is a Saturday: cat 4000 + 25% = 5000
        givenCatVisits(LocalDate.of(2013, 1, 5));

        InvoiceLine line = onlyInvoice("2013/01").getLines().get(0);

        assertEquals(1000L, line.getSurcharge());
        assertEquals(0L, line.getDiscount());
        assertEquals(5000L, line.getAmount());
    }

    @Test
    public void appliesTheCampaignDiscountToLizardsOnWeekdays() {
        // 2013/01/02 is a Wednesday: lizard 5200 - 15% = 4420
        Owner owner = ownerWithPet("lizard", LocalDate.of(2013, 1, 2));
        when(clinicService.findOwnerByLastName(eq(""))).thenReturn(Arrays.asList(owner));

        InvoiceLine line = onlyInvoice("2013/01").getLines().get(0);

        assertEquals(5200L, line.getUnitPrice());
        assertEquals(780L, line.getDiscount());
        assertEquals(4420L, line.getAmount());
    }

    @Test
    public void includesTheFirstAndLastDayOfTheMonthOnly() {
        givenCatVisits(LocalDate.of(2012, 12, 31), LocalDate.of(2013, 1, 1),
            LocalDate.of(2013, 1, 31), LocalDate.of(2013, 2, 1));

        Invoice invoice = onlyInvoice("2013/01");

        assertEquals(2, invoice.getLines().size());
    }

    @Test
    public void totalsTheInvoiceWithTenPercentTax() {
        // two weekday visits of 3800
        givenCatVisits(LocalDate.of(2013, 1, 2), LocalDate.of(2013, 1, 3));

        Invoice invoice = onlyInvoice("2013/01");

        assertEquals(7600L, invoice.getSubtotal());
        assertEquals(760L, invoice.getTax());
        assertEquals(8360L, invoice.getTotal());
    }

    @Test
    public void skipsOwnersWithoutChargeableVisits() {
        givenCatVisits(LocalDate.of(2013, 3, 1));

        assertTrue(service.close("2013/01").isEmpty());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsAPeriodThatIsNotYearSlashMonth() {
        service.close("2013-01");
    }

    private Invoice onlyInvoice(String period) {
        List<Invoice> invoices = service.close(period);
        assertEquals(1, invoices.size());
        return invoices.get(0);
    }

    private void givenCatVisits(LocalDate... dates) {
        Owner owner = ownerWithPet("cat", dates);
        when(clinicService.findOwnerByLastName(eq(""))).thenReturn(Arrays.asList(owner));
    }

    private Owner ownerWithPet(String petTypeName, LocalDate... visitDates) {
        PetType petType = new PetType();
        petType.setName(petTypeName);

        Pet pet = new Pet();
        pet.setId(CAT_PET_ID);
        pet.setName("Max");
        pet.setType(petType);

        Owner owner = new Owner();
        owner.setId(1);
        owner.setFirstName("Jean");
        owner.setLastName("Coleman");
        owner.addPet(pet);

        Collection<Visit> visits = new ArrayList<Visit>();
        for (LocalDate date : visitDates) {
            Visit visit = new Visit();
            visit.setDate(date);
            visit.setDescription("checkup");
            visits.add(visit);
        }
        when(clinicService.findVisitsByPetId(anyInt())).thenReturn(visits);
        return owner;
    }
}
