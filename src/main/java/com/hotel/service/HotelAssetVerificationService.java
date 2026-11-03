package com.hotel.service;

import com.hotel.model.Hotel;
import java.util.Map;

/**
 * Native Java service for verifying hotel assets, website reachability,
 * and high-resolution imagery without relying on external Python or Node.js scripts.
 */
public interface HotelAssetVerificationService {

    /**
     * Tests if an HTTP/HTTPS URL is reachable and returns an HTTP success code (200, 301, 302).
     */
    boolean isUrlReachable(String urlString);

    /**
     * Performs health checks on a hotel's official website and promotional image URL.
     */
    Map<String, Object> verifyHotelAssets(Hotel hotel);

    /**
     * Executes verification across cataloged hotels up to specified limit.
     */
    Map<String, Object> verifyAllHotelsAssets(int limit);
}