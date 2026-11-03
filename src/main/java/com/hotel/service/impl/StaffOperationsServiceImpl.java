package com.hotel.service.impl;

import com.hotel.model.*;
import com.hotel.model.enums.BookingStatus;
import com.hotel.model.enums.RoomStatus;
import com.hotel.repository.BookingRepository;
import com.hotel.repository.GuestRepository;
import com.hotel.repository.HotelRepository;
import com.hotel.repository.RoomRepository;
import com.hotel.service.StaffOperationsService;
import com.hotel.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class StaffOperationsServiceImpl implements StaffOperationsService {

    private static final Logger log = LoggerFactory.getLogger(StaffOperationsServiceImpl.class);

    private final RoomRepository roomRepository;
    private final BookingRepository bookingRepository;
    private final GuestRepository guestRepository;
    private final HotelRepository hotelRepository;
    private final NotificationService notificationService;

    @Autowired
    public StaffOperationsServiceImpl(RoomRepository roomRepository,
                                      BookingRepository bookingRepository,
                                      GuestRepository guestRepository,
                                      HotelRepository hotelRepository,
                                      NotificationService notificationService) {
        this.roomRepository = roomRepository;
        this.bookingRepository = bookingRepository;
        this.guestRepository = guestRepository;
        this.hotelRepository = hotelRepository;
        this.notificationService = notificationService;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getLiveOperationsSummary() {
        Map<String, Object> map = new LinkedHashMap<>();
        List<Room> allRooms = roomRepository.findAll();
        List<Booking> allBookings = bookingRepository.findAll();
        LocalDate today = LocalDate.now();

        int totalInventory = Math.max(allRooms.size(), 1);

        // Room status counts
        long availableCount = allRooms.stream().filter(r -> r.getStatus() == RoomStatus.AVAILABLE).count();
        long occupiedCount = allRooms.stream().filter(r -> r.getStatus() == RoomStatus.OCCUPIED).count();
        long maintenanceCount = allRooms.stream().filter(r -> r.getStatus() == RoomStatus.MAINTENANCE).count();
        long cleaningCount = allRooms.stream().filter(r -> r.getStatus() == RoomStatus.CLEANING).count();

        // Booking status counts
        long reservedCount = allBookings.stream().filter(b -> b.getStatus() == BookingStatus.CONFIRMED).count();
        long prebookedCount = allBookings.stream().filter(b -> b.getStatus() == BookingStatus.PENDING).count();
        long unreservedCount = allRooms.stream().filter(r -> r.getStatus() == RoomStatus.AVAILABLE && r.getStatus().isBookable()).count();

        // Percentage calculations
        int occupancyPct = (int) Math.round((occupiedCount * 100.0) / totalInventory);
        int availablePct = (int) Math.round((availableCount * 100.0) / totalInventory);
        int reservedPct = (int) Math.round((reservedCount * 100.0) / totalInventory);
        int prebookedPct = (int) Math.round((prebookedCount * 100.0) / totalInventory);
        int unreservedPct = (int) Math.round((unreservedCount * 100.0) / totalInventory);
        int maintenancePct = (int) Math.round((maintenanceCount * 100.0) / totalInventory);

        // Guest flow counts
        long currentGuests = allBookings.stream()
                .filter(b -> b.getStatus() == BookingStatus.CHECKED_IN
                        || (b.getStatus() == BookingStatus.CONFIRMED && !b.getCheckInDate().isAfter(today) && b.getCheckOutDate().isAfter(today)))
                .mapToInt(Booking::getNumberOfGuests)
                .sum();
        if (currentGuests == 0 && occupiedCount > 0) {
            currentGuests = occupiedCount * 2;
        }

        long todayArrivals = allBookings.stream()
                .filter(b -> b.getStatus().isActive() && today.equals(b.getCheckInDate()))
                .count();

        long todayDepartures = allBookings.stream()
                .filter(b -> b.getStatus().isActive() && today.equals(b.getCheckOutDate()))
                .count();

        long upcomingGuests = allBookings.stream()
                .filter(b -> b.getStatus().isActive() && b.getCheckInDate() != null && b.getCheckInDate().isAfter(today))
                .mapToInt(Booking::getNumberOfGuests)
                .sum();

        long completedStays = allBookings.stream()
                .filter(b -> b.getStatus() == BookingStatus.COMPLETED || b.getStatus() == BookingStatus.CHECKED_OUT)
                .count();

        long cancelledBookings = allBookings.stream()
                .filter(b -> b.getStatus() == BookingStatus.CANCELLED)
                .count();

        long guestRequestsCount = allBookings.stream()
                .filter(b -> b.getStatus().isActive() && b.getSpecialRequests() != null && !b.getSpecialRequests().isBlank())
                .count();

        map.put("totalInventory", totalInventory);
        map.put("occupancyPct", occupancyPct);
        map.put("occupiedCount", occupiedCount);
        map.put("availablePct", availablePct);
        map.put("availableCount", availableCount);
        map.put("reservedPct", reservedPct);
        map.put("reservedCount", reservedCount);
        map.put("prebookedPct", prebookedPct);
        map.put("prebookedCount", prebookedCount);
        map.put("unreservedPct", unreservedPct);
        map.put("unreservedCount", unreservedCount);
        map.put("maintenancePct", maintenancePct);
        map.put("maintenanceCount", maintenanceCount);
        map.put("cleaningCount", cleaningCount);

        map.put("currentGuests", currentGuests);
        map.put("todayArrivals", todayArrivals);
        map.put("todayDepartures", todayDepartures);
        map.put("upcomingGuests", upcomingGuests);
        map.put("completedStays", completedStays);
        map.put("cancelledBookings", cancelledBookings);
        map.put("guestRequestsCount", guestRequestsCount);

        return map;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getLiveOperationsList(String category) {
        List<Map<String, Object>> result = new ArrayList<>();
        List<Room> allRooms = roomRepository.findAll();
        List<Booking> allBookings = bookingRepository.findAll();
        LocalDate today = LocalDate.now();

        String cat = category != null ? category.trim().toUpperCase() : "OCCUPANCY";

        switch (cat) {
            case "OCCUPANCY":
            case "OCCUPIED":
            case "CURRENT_GUESTS":
                for (Booking b : allBookings) {
                    boolean isCurrentStay = (b.getStatus() == BookingStatus.CHECKED_IN)
                            || (b.getStatus() == BookingStatus.CONFIRMED && !b.getCheckInDate().isAfter(today) && b.getCheckOutDate().isAfter(today));
                    if (isCurrentStay) {
                        result.add(formatBookingSummary(b, "In Residence / Active Stay"));
                    }
                }
                // If rooms are marked OCCUPIED without explicit current booking, list those room units
                if (result.isEmpty()) {
                    for (Room r : allRooms) {
                        if (r.getStatus() == RoomStatus.OCCUPIED) {
                            result.add(formatRoomSummary(r, "Room Occupied"));
                        }
                    }
                }
                break;

            case "AVAILABLE":
            case "UNRESERVED":
                for (Room r : allRooms) {
                    if (r.getStatus() == RoomStatus.AVAILABLE) {
                        result.add(formatRoomSummary(r, "Ready for Guest Assignment"));
                    }
                }
                break;

            case "RESERVED":
                for (Booking b : allBookings) {
                    if (b.getStatus() == BookingStatus.CONFIRMED) {
                        result.add(formatBookingSummary(b, "Confirmed Reservation"));
                    }
                }
                break;

            case "PREBOOKED":
            case "PENDING":
                for (Booking b : allBookings) {
                    if (b.getStatus() == BookingStatus.PENDING) {
                        result.add(formatBookingSummary(b, "Pending Confirmation"));
                    }
                }
                break;

            case "TODAY_ARRIVALS":
            case "ARRIVALS":
                for (Booking b : allBookings) {
                    if (b.getStatus().isActive() && today.equals(b.getCheckInDate())) {
                        result.add(formatBookingSummary(b, "Arriving Today"));
                    }
                }
                break;

            case "TODAY_DEPARTURES":
            case "DEPARTURES":
                for (Booking b : allBookings) {
                    if (b.getStatus().isActive() && today.equals(b.getCheckOutDate())) {
                        result.add(formatBookingSummary(b, "Departing Today"));
                    }
                }
                break;

            case "UPCOMING_GUESTS":
            case "UPCOMING":
                for (Booking b : allBookings) {
                    if (b.getStatus().isActive() && b.getCheckInDate() != null && b.getCheckInDate().isAfter(today)) {
                        result.add(formatBookingSummary(b, "Upcoming Arrival"));
                    }
                }
                break;

            case "MAINTENANCE":
                for (Room r : allRooms) {
                    if (r.getStatus() == RoomStatus.MAINTENANCE) {
                        result.add(formatRoomSummary(r, "Under Active Maintenance"));
                    }
                }
                break;

            case "CLEANING":
                for (Room r : allRooms) {
                    if (r.getStatus() == RoomStatus.CLEANING) {
                        result.add(formatRoomSummary(r, "Housekeeping In Progress"));
                    }
                }
                break;

            case "COMPLETED":
                for (Booking b : allBookings) {
                    if (b.getStatus() == BookingStatus.COMPLETED || b.getStatus() == BookingStatus.CHECKED_OUT) {
                        result.add(formatBookingSummary(b, "Stay Concluded"));
                    }
                }
                break;

            case "CANCELLED":
                for (Booking b : allBookings) {
                    if (b.getStatus() == BookingStatus.CANCELLED) {
                        result.add(formatBookingSummary(b, "Cancelled Reservation"));
                    }
                }
                break;

            case "GUEST_REQUESTS":
            case "REQUESTS":
                for (Booking b : allBookings) {
                    if (b.getStatus().isActive() && b.getSpecialRequests() != null && !b.getSpecialRequests().isBlank()) {
                        result.add(formatBookingSummary(b, "Special Concierge Request"));
                    }
                }
                break;

            default:
                for (Booking b : allBookings) {
                    result.add(formatBookingSummary(b, b.getStatus().getDisplayName()));
                }
                break;
        }

        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getGuestBookingDetail(Long bookingId) {
        if (bookingId == null) return Collections.emptyMap();
        Optional<Booking> opt = bookingRepository.findById(bookingId);
        if (opt.isEmpty()) return Collections.emptyMap();

        Booking b = opt.get();
        LocalDate today = LocalDate.now();

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("bookingId", b.getId());
        map.put("bookingReference", b.getBookingReference());

        // Guest info
        if (b.getGuest() != null) {
            map.put("guestName", b.getGuest().getFullName());
            map.put("guestEmail", b.getGuest().getEmail());
            map.put("guestPhone", b.getGuest().getPhoneNumber());
            map.put("guestType", "Primary Guest");
            map.put("guestUsername", b.getGuest().getUsername());
        } else {
            map.put("guestName", "Walk-in Guest");
            map.put("guestEmail", "N/A");
            map.put("guestPhone", "N/A");
            map.put("guestType", "Standard Guest");
            map.put("guestUsername", "N/A");
        }

        // Additional travelers breakdown
        List<Map<String, Object>> travelers = new ArrayList<>();
        if (b.getGuestDetails() != null && !b.getGuestDetails().isEmpty()) {
            for (BookingGuestDetail d : b.getGuestDetails()) {
                Map<String, Object> t = new HashMap<>();
                t.put("name", d.getName() != null && !d.getName().isBlank() ? d.getName() : "Companion Guest");
                t.put("guestType", d.getGuestType() != null ? d.getGuestType() : "Adult");
                t.put("age", d.getAge() != null ? d.getAge() : "-");
                t.put("gender", d.getGender() != null ? d.getGender() : "-");
                travelers.add(t);
            }
        }
        map.put("travelers", travelers);

        // Hotel & Room
        if (b.getHotel() != null) {
            map.put("hotelName", b.getHotel().getName());
            map.put("city", b.getHotel().getCity());
            map.put("district", b.getHotel().getDistrict());
            map.put("state", b.getHotel().getState());
            map.put("address", b.getHotel().getAddress());
        } else {
            map.put("hotelName", "Royal Indian Stays Premier Property");
            map.put("city", "Bengaluru");
            map.put("district", "Bengaluru Urban");
            map.put("state", "Karnataka");
            map.put("address", "Palace Grounds");
        }

        if (b.getRoom() != null) {
            map.put("roomId", b.getRoom().getId());
            map.put("roomNumber", b.getRoom().getRoomNumber());
            map.put("floorNumber", b.getRoom().getFloorNumber());
            map.put("roomCategory", b.getRoom().getCategory() != null ? b.getRoom().getCategory().getDisplayName() : "Heritage Suite");
            map.put("roomStatus", b.getRoom().getStatus() != null ? b.getRoom().getStatus().getDisplayName() : "Available");
            map.put("roomRate", b.getRoom().getBasePricePerNight());
        } else {
            map.put("roomId", null);
            map.put("roomNumber", "Unassigned");
            map.put("floorNumber", "-");
            map.put("roomCategory", b.getRoomType() != null ? b.getRoomType() : "Deluxe Room");
            map.put("roomStatus", "Reserved");
            map.put("roomRate", 0.0);
        }

        map.put("checkInDate", b.getCheckInDate() != null ? b.getCheckInDate().toString() : "-");
        map.put("checkInTime", b.getCheckInTime() != null ? b.getCheckInTime().toString() : "14:00");
        map.put("checkOutDate", b.getCheckOutDate() != null ? b.getCheckOutDate().toString() : "-");
        map.put("checkOutTime", b.getCheckOutTime() != null ? b.getCheckOutTime().toString() : "11:00");
        map.put("numberOfGuests", b.getNumberOfGuests());
        map.put("totalAmount", b.getTotalAmount());
        map.put("bookingStatus", b.getStatus().name());
        map.put("bookingStatusDisplay", b.getStatus().getDisplayName());

        // Determine current stay lifecycle
        String stayStatus;
        if (b.getStatus() == BookingStatus.CHECKED_IN) {
            stayStatus = "Currently In Residence";
        } else if (b.getStatus() == BookingStatus.CHECKED_OUT || b.getStatus() == BookingStatus.COMPLETED) {
            stayStatus = "Stay Concluded / Checked Out";
        } else if (b.getStatus() == BookingStatus.CANCELLED) {
            stayStatus = "Reservation Cancelled";
        } else if (today.equals(b.getCheckInDate())) {
            stayStatus = "Arriving Today";
        } else if (today.equals(b.getCheckOutDate())) {
            stayStatus = "Departing Today";
        } else if (b.getCheckInDate() != null && b.getCheckInDate().isAfter(today)) {
            stayStatus = "Upcoming Arrival (" + b.getCheckInDate() + ")";
        } else {
            stayStatus = "Active Reservation";
        }
        map.put("currentStayStatus", stayStatus);

        map.put("specialRequests", b.getSpecialRequests() != null && !b.getSpecialRequests().isBlank()
                ? b.getSpecialRequests() : "None noted");

        return map;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getRoomOperationalDetail(Long roomId) {
        if (roomId == null) return Collections.emptyMap();
        Optional<Room> opt = roomRepository.findById(roomId);
        if (opt.isEmpty()) return Collections.emptyMap();

        Room r = opt.get();
        LocalDate today = LocalDate.now();

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("roomId", r.getId());
        map.put("roomNumber", r.getRoomNumber());
        map.put("floorNumber", r.getFloorNumber());
        map.put("category", r.getCategory() != null ? r.getCategory().getDisplayName() : "Deluxe");
        map.put("status", r.getStatus() != null ? r.getStatus().name() : "AVAILABLE");
        map.put("statusDisplay", r.getStatus() != null ? r.getStatus().getDisplayName() : "Available");
        map.put("basePrice", r.getBasePricePerNight());
        map.put("imageUrl", r.getImageUrl());
        map.put("description", r.getDescription());

        Hotel associatedHotel = bookingRepository.findAll().stream()
                .filter(b -> b.getRoom() != null && r.getId().equals(b.getRoom().getId()) && b.getHotel() != null)
                .map(Booking::getHotel)
                .findFirst()
                .orElseGet(() -> hotelRepository.findAll().stream().findFirst().orElse(null));

        if (associatedHotel != null) {
            map.put("hotelName", associatedHotel.getName());
            map.put("city", associatedHotel.getCity());
            map.put("district", associatedHotel.getDistrict());
            map.put("state", associatedHotel.getState());
        } else {
            map.put("hotelName", "Royal Indian Stays");
            map.put("city", "Bengaluru");
            map.put("district", "Bengaluru Urban");
            map.put("state", "Karnataka");
        }

        // Find associated current or upcoming reservation
        List<Booking> roomBookings = bookingRepository.findAll().stream()
                .filter(b -> b.getRoom() != null && r.getId().equals(b.getRoom().getId()) && b.getStatus().isActive())
                .sorted(Comparator.comparing(Booking::getCheckInDate))
                .collect(Collectors.toList());

        Booking currentBooking = roomBookings.stream()
                .filter(b -> b.getStatus() == BookingStatus.CHECKED_IN
                        || (!b.getCheckInDate().isAfter(today) && b.getCheckOutDate().isAfter(today)))
                .findFirst()
                .orElse(null);

        Booking upcomingBooking = roomBookings.stream()
                .filter(b -> b.getCheckInDate() != null && !b.getCheckInDate().isBefore(today))
                .findFirst()
                .orElse(null);

        Booking relevant = currentBooking != null ? currentBooking : upcomingBooking;

        if (relevant != null) {
            map.put("hasGuest", true);
            map.put("bookingId", relevant.getId());
            map.put("bookingReference", relevant.getBookingReference());
            map.put("guestName", relevant.getGuest() != null ? relevant.getGuest().getFullName() : "Guest");
            map.put("guestPhone", relevant.getGuest() != null ? relevant.getGuest().getPhoneNumber() : "N/A");
            map.put("checkInDate", relevant.getCheckInDate().toString());
            map.put("checkOutDate", relevant.getCheckOutDate().toString());
            map.put("numberOfGuests", relevant.getNumberOfGuests());
            map.put("specialRequests", relevant.getSpecialRequests() != null ? relevant.getSpecialRequests() : "");
            map.put("bookingStatus", relevant.getStatus().name());
        } else {
            map.put("hasGuest", false);
        }

        return map;
    }

    @Override
    public boolean checkInBooking(Long bookingId) {
        if (bookingId == null) return false;
        Optional<Booking> opt = bookingRepository.findById(bookingId);
        if (opt.isPresent()) {
            Booking b = opt.get();
            b.setStatus(BookingStatus.CHECKED_IN);
            bookingRepository.save(b);
            if (b.getRoom() != null) {
                b.getRoom().setStatus(RoomStatus.OCCUPIED);
                roomRepository.save(b.getRoom());
            }
            log.info("Staff operational check-in completed for booking: {}", b.getBookingReference());
            return true;
        }
        return false;
    }

    @Override
    public boolean checkOutBooking(Long bookingId) {
        if (bookingId == null) return false;
        Optional<Booking> opt = bookingRepository.findById(bookingId);
        if (opt.isPresent()) {
            Booking b = opt.get();
            b.setStatus(BookingStatus.CHECKED_OUT);
            bookingRepository.save(b);
            if (b.getRoom() != null) {
                b.getRoom().setStatus(RoomStatus.AVAILABLE);
                roomRepository.save(b.getRoom());
            }
            log.info("Staff operational check-out completed for booking: {}", b.getBookingReference());
            return true;
        }
        return false;
    }

    @Override
    public boolean updateRoomStatus(Long roomId, RoomStatus newStatus) {
        if (roomId == null || newStatus == null) return false;
        Optional<Room> opt = roomRepository.findById(roomId);
        if (opt.isPresent()) {
            Room r = opt.get();
            RoomStatus oldStatus = r.getStatus();
            r.setStatus(newStatus);
            roomRepository.save(r);
            log.info("Staff operational room status updated: Room {} -> {}", r.getRoomNumber(), newStatus);
            try {
                if (notificationService != null) {
                    notificationService.notifyRoomStatusChange(r, oldStatus, newStatus, "Staff Operations");
                }
            } catch (Exception ex) {
                log.warn("Room status change notification dispatch failed: {}", ex.getMessage());
            }
            return true;
        }
        return false;
    }

    private Map<String, Object> formatBookingSummary(Booking b, String operationalNote) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "BOOKING");
        item.put("id", b.getId());
        item.put("reference", b.getBookingReference());
        item.put("guestName", b.getGuest() != null ? b.getGuest().getFullName() : "Guest");
        item.put("guestPhone", b.getGuest() != null ? b.getGuest().getPhoneNumber() : "");
        item.put("hotelName", b.getHotel() != null ? b.getHotel().getName() : "Royal Stays");
        item.put("roomNumber", b.getRoom() != null ? b.getRoom().getRoomNumber() : "TBD");
        item.put("category", b.getRoom() != null && b.getRoom().getCategory() != null ? b.getRoom().getCategory().getDisplayName() : b.getRoomType());
        item.put("checkIn", b.getCheckInDate() != null ? b.getCheckInDate().toString() : "-");
        item.put("checkOut", b.getCheckOutDate() != null ? b.getCheckOutDate().toString() : "-");
        item.put("guests", b.getNumberOfGuests());
        item.put("status", b.getStatus().name());
        item.put("statusDisplay", b.getStatus().getDisplayName());
        item.put("operationalNote", operationalNote);
        item.put("specialRequests", b.getSpecialRequests() != null ? b.getSpecialRequests() : "");
        return item;
    }

    private Map<String, Object> formatRoomSummary(Room r, String operationalNote) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "ROOM");
        item.put("id", r.getId());
        item.put("reference", "RM-" + r.getRoomNumber());
        item.put("guestName", "-");
        item.put("guestPhone", "-");

        Hotel associatedHotel = bookingRepository.findAll().stream()
                .filter(b -> b.getRoom() != null && r.getId().equals(b.getRoom().getId()) && b.getHotel() != null)
                .map(Booking::getHotel)
                .findFirst()
                .orElseGet(() -> hotelRepository.findAll().stream().findFirst().orElse(null));

        item.put("hotelName", associatedHotel != null ? associatedHotel.getName() : "Royal Stays");
        item.put("roomNumber", r.getRoomNumber());
        item.put("category", r.getCategory() != null ? r.getCategory().getDisplayName() : "Suite");
        item.put("checkIn", "-");
        item.put("checkOut", "-");
        item.put("guests", r.getMaxOccupancy());
        item.put("status", r.getStatus().name());
        item.put("statusDisplay", r.getStatus().getDisplayName());
        item.put("operationalNote", operationalNote);
        item.put("specialRequests", "");
        return item;
    }
}
