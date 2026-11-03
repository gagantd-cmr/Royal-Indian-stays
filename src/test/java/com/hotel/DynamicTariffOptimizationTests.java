package com.hotel;

import com.hotel.model.Room;
import com.hotel.model.enums.RoomCategory;
import com.hotel.service.DynamicTariffService;
import com.hotel.util.TariffCalculationHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Verifies the Java Dynamic Tariff Optimizer algorithms, surge multipliers,
 * real-time occupancy calculations, and Admin reporting endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DynamicTariffOptimizationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DynamicTariffService dynamicTariffService;

    @Test
    @DisplayName("Verify Dynamic Tariff Multipliers Across All Four Occupancy Tiers")
    void testDynamicTariffFourTiers() {
        double base = 10000.0;

        // Tier 1: Low Demand (< 40%) -> 0.90x Discount
        double tier1 = TariffCalculationHelper.calculateEstimatedTariff(base, 25.0);
        assertEquals(9000.0, tier1, 0.01, "Low occupancy (<40%) must apply 0.90x discount");

        // Tier 2: Standard Nominal (40% - 70%) -> 1.00x Base
        double tier2 = TariffCalculationHelper.calculateEstimatedTariff(base, 50.0);
        assertEquals(10000.0, tier2, 0.01, "Nominal occupancy (40%-70%) must apply 1.00x standard rate");

        // Tier 3: High Demand (70% - 90%) -> 1.25x Surge
        double tier3 = TariffCalculationHelper.calculateEstimatedTariff(base, 80.0);
        assertEquals(12500.0, tier3, 0.01, "High occupancy (70%-90%) must apply 1.25x surge");

        // Tier 4: Peak Sold-Out (> 90%) -> 1.50x Peak Surge
        double tier4 = TariffCalculationHelper.calculateEstimatedTariff(base, 95.0);
        assertEquals(15000.0, tier4, 0.01, "Peak occupancy (>90%) must apply 1.50x peak surge");
    }

    @Test
    @DisplayName("Verify DynamicTariffService calculation on Room entity")
    void testServiceCalculationOnRoom() {
        Room deluxe = new Room("TEST-101", RoomCategory.DELUXE, 4500.0, 1, 2);
        double calculated = dynamicTariffService.calculateDynamicTariff(deluxe, LocalDate.now(), LocalDate.now().plusDays(2));
        assertTrue(calculated > 0, "Calculated dynamic tariff must be strictly positive");
        assertTrue(calculated >= 4500.0 * 0.90, "Calculated tariff should not drop below maximum discount");
    }

    @Test
    @DisplayName("Verify Dynamic Tariff Analytics payload structure")
    void testAnalyticsPayload() {
        Map<String, Object> analytics = dynamicTariffService.getDynamicTariffAnalytics();
        assertNotNull(analytics);
        assertTrue(analytics.containsKey("currentOccupancy"));
        assertTrue(analytics.containsKey("pricingMultiplier"));
        assertTrue(analytics.containsKey("demandTier"));
        assertTrue(analytics.containsKey("currentDeluxeTariff"));
        assertTrue(analytics.containsKey("currentSuiteTariff"));
        assertTrue(analytics.containsKey("currentPresidentialTariff"));
        assertTrue(analytics.containsKey("tierRows"));
    }

    @Test
    @WithMockUser(username = "GAGAN", roles = {"ADMIN"})
    @DisplayName("Verify Admin Reports View loads with Dynamic Tariff Model")
    void testAdminReportsEndpoint() throws Exception {
        mockMvc.perform(get("/admin/reports"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/reports"))
                .andExpect(model().attributeExists("currentOccupancy"))
                .andExpect(model().attributeExists("pricingMultiplier"))
                .andExpect(model().attributeExists("demandTier"));
    }

    @Test
    @WithMockUser(username = "GAGAN", roles = {"ADMIN"})
    @DisplayName("Verify Admin Rooms View loads with Room Catalog")
    void testAdminRoomsEndpoint() throws Exception {
        mockMvc.perform(get("/admin/rooms"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/rooms"))
                .andExpect(model().attributeExists("rooms"));
    }

    @Test
    @WithMockUser(username = "GAGAN", roles = {"ADMIN"})
    @DisplayName("Verify Dynamic Tariff JSON API Endpoint")
    void testDynamicTariffApiEndpoint() throws Exception {
        mockMvc.perform(get("/admin/api/operations/dynamic-tariff")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentOccupancy").exists())
                .andExpect(jsonPath("$.pricingMultiplier").exists())
                .andExpect(jsonPath("$.demandTier").exists());
    }
}