package com.hotel;

import com.hotel.service.HotelAssetVerificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class HotelAssetVerificationServiceTests {

    @Autowired
    private HotelAssetVerificationService assetVerificationService;

    @Test
    @DisplayName("Verify HotelAssetVerificationService is instantiated in Spring context")
    void testServiceInstantiation() {
        assertNotNull(assetVerificationService);
    }

    @Test
    @DisplayName("Verify URL Reachability with empty/invalid inputs")
    void testInvalidUrlsReturnFalse() {
        assertFalse(assetVerificationService.isUrlReachable(null));
        assertFalse(assetVerificationService.isUrlReachable(""));
        assertFalse(assetVerificationService.isUrlReachable("not-a-valid-url"));
    }

    @Test
    @DisplayName("Verify Batch Hotel Asset Verification in Pure Java")
    void testVerifyAllHotelsAssets() {
        Map<String, Object> summary = assetVerificationService.verifyAllHotelsAssets(2);
        assertNotNull(summary);
        assertTrue(summary.containsKey("totalChecked"));
        assertTrue(summary.containsKey("websitesReachable"));
        assertTrue(summary.containsKey("imagesReachable"));
    }
}