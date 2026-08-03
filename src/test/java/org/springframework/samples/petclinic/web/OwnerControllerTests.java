package org.springframework.samples.petclinic.web;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Before;
import org.junit.Test;
import org.springframework.samples.petclinic.model.Owner;
import org.springframework.samples.petclinic.service.ClinicService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Pins the owner search behavior (BR-03, BR-04, BR-05).
 */
public class OwnerControllerTests {

    private ClinicService clinicService;

    private MockMvc mockMvc;

    @Before
    public void setUp() {
        clinicService = mock(ClinicService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new OwnerController(clinicService)).build();
    }

    @Test
    public void searchesAllOwnersWhenNoLastNameIsGiven() throws Exception {
        when(clinicService.findOwnerByLastName(eq(""))).thenReturn(Arrays.asList(owner(1, "Franklin"), owner(2, "Davis")));

        mockMvc.perform(get("/owners"))
            .andExpect(status().isOk())
            .andExpect(view().name("owners/ownersList"))
            .andExpect(model().attributeExists("selections"));
    }

    @Test
    public void redirectsToTheDetailPageOnASingleHit() throws Exception {
        when(clinicService.findOwnerByLastName(eq("Franklin"))).thenReturn(Arrays.asList(owner(1, "Franklin")));

        mockMvc.perform(get("/owners").param("lastName", "Franklin"))
            .andExpect(status().is3xxRedirection())
            .andExpect(view().name("redirect:/owners/1"));
    }

    @Test
    public void reportsNotFoundWhenNothingMatches() throws Exception {
        when(clinicService.findOwnerByLastName(eq("Unknown"))).thenReturn(Collections.<Owner>emptyList());

        mockMvc.perform(get("/owners").param("lastName", "Unknown"))
            .andExpect(status().isOk())
            .andExpect(view().name("owners/findOwners"))
            .andExpect(model().attributeHasFieldErrors("owner", "lastName"));
    }

    private Owner owner(int id, String lastName) {
        Owner owner = new Owner();
        owner.setId(id);
        owner.setFirstName("Test");
        owner.setLastName(lastName);
        owner.setAddress("110 W. Liberty St.");
        owner.setCity("Madison");
        owner.setTelephone("6085551023");
        return owner;
    }
}
