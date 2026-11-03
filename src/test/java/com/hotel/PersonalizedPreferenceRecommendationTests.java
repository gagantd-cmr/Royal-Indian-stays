package com.hotel;

import com.hotel.model.Guest;
import com.hotel.model.GuestPreference;
import com.hotel.model.enums.RoomCategory;
import com.hotel.repository.GuestPreferenceRepository;
import com.hotel.repository.GuestRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PersonalizedPreferenceRecommendationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GuestRepository guestRepository;

    @Autowired
    private GuestPreferenceRepository preferenceRepository;

    @Test
    @WithMockUser(username = "stage5_guest_a", roles = {"GUEST"})
    @DisplayName("Verify Recommendations endpoint handles authenticated guest with saved preferences")
    void testRecommendationsWithGuestPreferences() throws Exception {
        Guest guest = guestRepository.findByUsernameIgnoreCase("stage5_guest_a").orElse(null);
        if (guest != null) {
            GuestPreference pref = new GuestPreference(guest, RoomCategory.DELUXE, "KING");
            preferenceRepository.save(pref);
        }

        mockMvc.perform(get("/guest/recommendations"))
                .andExpect(status().isOk())
                .andExpect(view().name("guest/recommendations"))
                .andExpect(model().attributeExists("hotels"))
                .andExpect(model().attributeExists("isPersonalized"));
    }
}