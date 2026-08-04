package org.springframework.samples.petclinic.service.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.springframework.samples.petclinic.model.Owner;
import org.springframework.samples.petclinic.model.Pet;
import org.springframework.samples.petclinic.model.PetType;
import org.springframework.samples.petclinic.model.Visit;
import org.springframework.samples.petclinic.service.ClinicService;

/**
 * Characterization tests for {@link MonthlyClosingService#close(String)} (BR-12..BR-18). Real fee
 * master and discount plugin are used; only the data source ({@link ClinicService}) is mocked.
 *
 * <p>These pin several behaviors that differ from an obvious reading of the spec and are documented
 * as discrepancies in {@code docs/as-is/06-open-questions.md}:
 * <ul>
 *   <li>the configured {@code closing-day=25} does NOT bound the period; the window is the full
 *       calendar month (BR-13 / OQ-1);</li>
 *   <li>visits with a null date are silently skipped;</li>
 *   <li>owners with no chargeable line are omitted entirely.</li>
 * </ul>
 */
public class MonthlyClosingServiceTests {

    private ClinicService clinicService;
    private MonthlyClosingService service;
    private int nextPetId = 100;

    @Before
    public void setUp() {
        clinicService = mock(ClinicService.class);
        service = new MonthlyClosingService(clinicService, new BillingRateLoader(),
            new DiscountPluginLoader());
    }

    private Owner owner(int id, String last, String first, Pet... pets) {
        Owner owner = new Owner();
        owner.setId(id);
        owner.setLastName(last);
        owner.setFirstName(first);
        for (Pet pet : pets) {
            owner.addPet(pet);
        }
        return owner;
    }

    private Pet pet(String typeName, String name) {
        Pet pet = new Pet();
        pet.setId(nextPetId++);
        pet.setName(name);
        if (typeName != null) {
            PetType type = new PetType();
            type.setName(typeName);
            pet.setType(type);
        }
        return pet;
    }

    private Visit visit(LocalDate date, String description) {
        Visit visit = new Visit();
        visit.setDate(date);
        visit.setDescription(description);
        return visit;
    }

    private void visitsFor(Pet pet, Visit... visits) {
        when(clinicService.findVisitsByPetId(pet.getId())).thenReturn(Arrays.asList(visits));
    }

    @Test
    public void weekdayDogInvoiceMatchesPinnedArithmetic() {
        Pet dog = pet("dog", "Rex");
        Owner o = owner(1, "Franklin", "George", dog);
        when(clinicService.findOwnerByLastName("")).thenReturn(Collections.singletonList(o));
        visitsFor(dog, visit(LocalDate.of(2013, 1, 4), "annual")); // Friday

        List<Invoice> invoices = service.close("2013/01");

        assertThat(invoices).hasSize(1);
        Invoice inv = invoices.get(0);
        assertThat(inv.getOwnerName()).isEqualTo("Franklin George");
        assertThat(inv.getLines()).hasSize(1);
        InvoiceLine l = inv.getLines().get(0);
        assertThat(l.getUnitPrice()).isEqualTo(4500L);
        assertThat(l.getSurcharge()).isEqualTo(0L);
        assertThat(l.getDiscount()).isEqualTo(225L);
        assertThat(l.getAmount()).isEqualTo(4275L);
        assertThat(inv.getSubtotal()).isEqualTo(4275L);
        assertThat(inv.getTax()).isEqualTo(428L);
        assertThat(inv.getTotal()).isEqualTo(4703L);
    }

    @Test
    public void weekendVisitGetsHolidaySurchargeAndNoDiscount() {
        Pet dog = pet("dog", "Rex");
        Owner o = owner(1, "Franklin", "George", dog);
        when(clinicService.findOwnerByLastName("")).thenReturn(Collections.singletonList(o));
        visitsFor(dog, visit(LocalDate.of(2013, 1, 5), "weekend")); // Saturday

        InvoiceLine l = service.close("2013/01").get(0).getLines().get(0);
        assertThat(l.getSurcharge()).isEqualTo(1125L); // round(4500 * 0.25)
        assertThat(l.getDiscount()).isEqualTo(0L);
        assertThat(l.getAmount()).isEqualTo(5625L);
    }

    @Test
    public void nullPetTypeIsChargedAtWildcardRate() {
        Pet unknown = pet(null, "Mystery");
        Owner o = owner(1, "Franklin", "George", unknown);
        when(clinicService.findOwnerByLastName("")).thenReturn(Collections.singletonList(o));
        visitsFor(unknown, visit(LocalDate.of(2013, 1, 4), "checkup")); // Friday

        InvoiceLine l = service.close("2013/01").get(0).getLines().get(0);
        assertThat(l.getPetType()).isEqualTo("*");
        assertThat(l.getUnitPrice()).isEqualTo(3800L);
        assertThat(l.getDiscount()).isEqualTo(190L); // round(3800 * 0.05)
        assertThat(l.getAmount()).isEqualTo(3610L);
    }

    @Test
    public void visitsWithNullDateAreSkipped() {
        Pet dog = pet("dog", "Rex");
        Owner o = owner(1, "Franklin", "George", dog);
        when(clinicService.findOwnerByLastName("")).thenReturn(Collections.singletonList(o));
        visitsFor(dog, visit(null, "no date"), visit(LocalDate.of(2013, 1, 4), "dated"));

        assertThat(service.close("2013/01").get(0).getLines()).hasSize(1);
    }

    @Test
    public void periodBoundariesAreInclusiveAndNextMonthIsExcluded() {
        Pet dog = pet("dog", "Rex");
        Owner o = owner(1, "Franklin", "George", dog);
        when(clinicService.findOwnerByLastName("")).thenReturn(Collections.singletonList(o));
        visitsFor(dog,
            visit(LocalDate.of(2013, 1, 1), "first day"),
            visit(LocalDate.of(2013, 1, 31), "last day"),
            visit(LocalDate.of(2013, 2, 1), "next month"));

        assertThat(service.close("2013/01").get(0).getLines()).hasSize(2);
    }

    @Test
    public void closingDayDoesNotBoundThePeriod() {
        // OQ-1: closing-day=25 is loaded but ignored; a visit on the 26th is still in January.
        Pet dog = pet("dog", "Rex");
        Owner o = owner(1, "Franklin", "George", dog);
        when(clinicService.findOwnerByLastName("")).thenReturn(Collections.singletonList(o));
        visitsFor(dog, visit(LocalDate.of(2013, 1, 26), "after closing day"));

        assertThat(service.close("2013/01").get(0).getLines()).hasSize(1);
    }

    @Test
    public void ownersWithNoChargeableLinesAreExcluded() {
        Pet withVisit = pet("dog", "Rex");
        Pet withoutVisit = pet("cat", "Milo");
        Owner billed = owner(1, "Franklin", "George", withVisit);
        Owner notBilled = owner(2, "Davis", "Betty", withoutVisit);
        when(clinicService.findOwnerByLastName(""))
            .thenReturn(new ArrayList<Owner>(Arrays.asList(billed, notBilled)));
        visitsFor(withVisit, visit(LocalDate.of(2013, 1, 4), "annual"));
        visitsFor(withoutVisit); // no visits

        List<Invoice> invoices = service.close("2013/01");
        assertThat(invoices).hasSize(1);
        assertThat(invoices.get(0).getOwnerId()).isEqualTo(1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void malformedPeriodIsRejected() {
        service.close("2013-01");
    }
}
