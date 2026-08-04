package org.springframework.samples.petclinic.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.Test;
import org.springframework.samples.petclinic.model.Pet;
import org.springframework.samples.petclinic.model.PetType;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;

/**
 * Characterization tests for {@link PetValidator} (P-UI validation): name and birth date are always
 * required; pet type is required only for new (unsaved) pets.
 */
public class PetValidatorTests {

    private final PetValidator validator = new PetValidator();

    private static Pet pet(Integer id, String name, PetType type, LocalDate birthDate) {
        Pet pet = new Pet();
        pet.setId(id);
        pet.setName(name);
        pet.setType(type);
        pet.setBirthDate(birthDate);
        return pet;
    }

    private static PetType type() {
        PetType type = new PetType();
        type.setName("dog");
        return type;
    }

    private Errors validate(Pet pet) {
        Errors errors = new BeanPropertyBindingResult(pet, "pet");
        validator.validate(pet, errors);
        return errors;
    }

    @Test
    public void supportsPetOnly() {
        assertThat(validator.supports(Pet.class)).isTrue();
        assertThat(validator.supports(Object.class)).isFalse();
    }

    @Test
    public void fullyPopulatedPetHasNoErrors() {
        assertThat(validate(pet(1, "Rex", type(), LocalDate.of(2010, 1, 1))).hasErrors()).isFalse();
    }

    @Test
    public void missingNameIsRejected() {
        assertThat(validate(pet(1, "", type(), LocalDate.of(2010, 1, 1)))
            .getFieldErrorCount("name")).isEqualTo(1);
    }

    @Test
    public void missingBirthDateIsRejected() {
        assertThat(validate(pet(1, "Rex", type(), null))
            .getFieldErrorCount("birthDate")).isEqualTo(1);
    }

    @Test
    public void newPetWithoutTypeIsRejected() {
        assertThat(validate(pet(null, "Rex", null, LocalDate.of(2010, 1, 1)))
            .getFieldErrorCount("type")).isEqualTo(1);
    }

    @Test
    public void existingPetWithoutTypeIsAccepted() {
        // type is only required while the pet is new (id == null).
        assertThat(validate(pet(5, "Rex", null, LocalDate.of(2010, 1, 1)))
            .getFieldErrorCount("type")).isEqualTo(0);
    }
}
