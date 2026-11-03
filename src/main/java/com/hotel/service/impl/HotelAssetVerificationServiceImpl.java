package com.hotel.service.impl;

import com.hotel.model.Hotel;
import com.hotel.repository.HotelRepository;
import com.hotel.service.HotelAssetVerificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

@Service
public class HotelAssetVerificationServiceImpl implements HotelAssetVerificationService {

    private static final Logger log = LoggerFactory.getLogger(HotelAssetVerificationServiceImpl.class);

    private final HotelRepository hotelRepository;
    private final HttpClient httpClient;

    @Autowired
    public HotelAssetVerificationServiceImpl(HotelRepository hotelRepository) {
        this.hotelRepository = hotelRepository;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(4))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public boolean isUrlReachable(String urlString) {
        if (urlString == null || urlString.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(urlString.trim());
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(5))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) RoyalIndianStaysVerifier/1.0")
                    .GET()
                    .build();

            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            int code = response.statusCode();
            return code >= 200 && code < 400;
        } catch (Exception ex) {
            log.debug("Verification check failed for URL {}: {}", urlString, ex.getMessage());
            return false;
        }
    }

    @Override
    public Map<String, Object> verifyHotelAssets(Hotel hotel) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (hotel == null) {
            result.put("status", "NOT_FOUND");
            return result;
        }

        result.put("hotelId", hotel.getId());
        result.put("hotelName", hotel.getName());

        boolean siteOk = isUrlReachable(hotel.getOfficialWebsite());
        boolean imgOk = isUrlReachable(hotel.getImageUrl());

        result.put("officialWebsite", hotel.getOfficialWebsite());
        result.put("websiteValid", siteOk);
        result.put("imageUrl", hotel.getImageUrl());
        result.put("imageValid", imgOk);
        result.put("fullyVerified", siteOk && imgOk);

        return result;
    }

    @Override
    public Map<String, Object> verifyAllHotelsAssets(int limit) {
        Map<String, Object> summary = new LinkedHashMap<>();
        List<Hotel> sample = hotelRepository.findAll().stream()
                .limit(limit > 0 ? limit : 10)
                .toList();

        List<Map<String, Object>> verifiedList = sample.parallelStream()
                .map(this::verifyHotelAssets)
                .toList();

        long webCount = verifiedList.stream().filter(r -> Boolean.TRUE.equals(r.get("websiteValid"))).count();
        long imgCount = verifiedList.stream().filter(r -> Boolean.TRUE.equals(r.get("imageValid"))).count();

        summary.put("totalChecked", verifiedList.size());
        summary.put("websitesReachable", webCount);
        summary.put("imagesReachable", imgCount);
        summary.put("details", verifiedList);

        return summary;
    }
}