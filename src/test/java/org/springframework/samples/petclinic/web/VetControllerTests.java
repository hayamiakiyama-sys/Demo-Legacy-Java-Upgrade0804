package org.springframework.samples.petclinic.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;
import org.springframework.samples.petclinic.model.Specialty;
import org.springframework.samples.petclinic.model.Vet;
import org.springframework.samples.petclinic.service.ClinicService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Characterization tests for {@link VetController} (P-UI vet list, P-API JSON) using standalone
 * MockMvc with a mocked {@link ClinicService}.
 */
public class VetControllerTests {

    private MockMvc mockMvc;

    @Before
    public void setUp() {
        ClinicService clinicService = mock(ClinicService.class);
        Vet james = new Vet();
        james.setId(1);
        james.setFirstName("James");
        james.setLastName("Carter");
        Vet helen = new Vet();
        helen.setId(2);
        helen.setFirstName("Helen");
        helen.setLastName("Leary");
        Specialty radiology = new Specialty();
        radiology.setId(1);
        radiology.setName("radiology");
        helen.addSpecialty(radiology);
        when(clinicService.findVets()).thenReturn(Arrays.asList(james, helen));

        mockMvc = MockMvcBuilders.standaloneSetup(new VetController(clinicService)).build();
    }

    @Test
    public void htmlListReturnsViewAndModel() throws Exception {
        mockMvc.perform(get("/vets.html"))
            .andExpect(status().isOk())
            .andExpect(view().name("vets/vetList"))
            .andExpect(model().attributeExists("vets"));
    }

    @Test
    public void jsonListReturnsVetListArray() throws Exception {
        mockMvc.perform(get("/vets.json"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.vetList[0].firstName").value("James"))
            .andExpect(jsonPath("$.vetList[1].lastName").value("Leary"))
            .andExpect(jsonPath("$.vetList[1].nrOfSpecialties").value(1));
    }
}
