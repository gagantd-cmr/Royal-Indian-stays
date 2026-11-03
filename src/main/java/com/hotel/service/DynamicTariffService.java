package com.hotel.service;

import com.hotel.model.Room;
import java.time.LocalDate;
import java.util.Map;

/**
 * Service defining the Dynamic Tariff Optimizer logic for Royal Indian Stays.
 * Dynamically calculates room tariffs based on real-time hotel and system occupancy,
 * demand surge curves, and seasonal demand factors in pure Java.
 */
public interface DynamicTariffService {

    double calculateDynamicTariff(Room room, LocalDate checkIn, LocalDate checkOut);

    double calculateDynamicTariff(double basePrice, double occupancyPercentage);

    double calculateHotelOccupancy(Long hotelId, LocalDate checkIn, LocalDate checkOut);

    double calculateSystemOccupancy();

    Map<String, Object> getDynamicTariffAnalytics();

    void updateAllRoomDynamicTariffs();
}