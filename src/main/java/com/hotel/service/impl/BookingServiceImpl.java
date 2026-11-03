package com.hotel.service.impl;

import com.hotel.dto.BookingRequestDto;
import com.hotel.exception.BookingException;
import com.hotel.model.Booking;
import com.hotel.model.Guest;
import com.hotel.model.Hotel;
import com.hotel.model.Room;
import com.hotel.model.enums.BookingStatus;
import com.hotel.model.enums.RoomStatus;
import com.hotel.repository.BookingRepository;
import com.hotel.repository.GuestRepository;
import com.hotel.repository.HotelRepository;
import com.hotel.repository.RoomRepository;
import com.hotel.service.BookingService;
import com.hotel.service.DynamicTariffService;
import com.hotel.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service implementation for Stage 5 Guest Booking Lifecycle and Reservation Management.
 * Manages booking creation, date-range availability verification, price locking,
 * dynamic tariff optimization, real-time notification dispatch,
 * automatic completion transitions, and role-scoped access control in pure Java.
 */
@Service
@Transactional
public class BookingServiceImpl implements BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingServiceImpl.class);

    private final BookingRepository bookingRepository;
    private final GuestRepository guestRepository;
    private final RoomRepository roomRepository;
    private final HotelRepository hotelRepository;
    private final DynamicTariffService dynamicTariffService;
    private final NotificationService notificationService;

    @Autowired
    public BookingServiceImpl(BookingRepository bookingRepository,
                              GuestRepository guestRepository,
                              RoomRepository roomRepository,
                              HotelRepository hotelRepository,
                              DynamicTariffService dynamicTariffService,
                              NotificationService notificationService) {
        this.bookingRepository = bookingRepository;
        this.guestRepository = guestRepository;
        this.roomRepository = roomRepository;
        this.hotelRepository = hotelRepository;
        this.dynamicTariffService = dynamicTariffService;
        this.notificationService = notificationService;
    }

    @Override
    @Transactional
    public List<Booking> getAllBookings() {
        processAutomaticStatusTransitions();
        return bookingRepository.findAll();
    }

    @Override
    @Transactional
    public List<Booking> getBookingsByGuestId(Long guestId) {
        if (guestId == null) {
            return Collections.emptyList();
        }
        processAutomaticStatusTransitions();
        return bookingRepository.findByGuestId(guestId);
    }

    @Override
    @Transactional
    public List<Booking> getActiveBookingsByGuest(Long guestId) {
        if (guestId == null) {
            return Collections.emptyList();
        }
        processAutomaticStatusTransitions();
        LocalDate today = LocalDate.now();
        return bookingRepository.findByGuestId(guestId).stream()
                .filter(b -> b.getStatus() == BookingStatus.CONFIRMED || b.getStatus() == BookingStatus.CHECKED_IN)
                .filter(b -> b.getCheckOutDate() != null && !b.getCheckOutDate().isBefore(today))
                .sorted(Comparator.comparing(Booking::getCheckInDate))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public List<Booking> getPastBookingsByGuest(Long guestId) {
        if (guestId == null) {
            return Collections.emptyList();
        }
        processAutomaticStatusTransitions();
        LocalDate today = LocalDate.now();
        List<Booking> all = bookingRepository.findByGuestId(guestId);
        return all.stream()
                .filter(b -> (b.getStatus() == BookingStatus.COMPLETED ||
                              b.getStatus() == BookingStatus.CHECKED_OUT ||
                              b.getStatus() == BookingStatus.CANCELLED ||
                              (b.getCheckOutDate() != null && b.getCheckOutDate().isBefore(today))))
                .sorted(Comparator.comparing(Booking::getCheckOutDate).reversed())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public Optional<Booking> getBookingById(Long id) {
        if (id == null) return Optional.empty();
        processAutomaticStatusTransitions();
        return bookingRepository.findById(id);
    }

    @Override
    @Transactional
    public Optional<Booking> getBookingByReference(String reference) {
        if (reference == null || reference.isBlank()) return Optional.empty();
        processAutomaticStatusTransitions();
        return bookingRepository.findByBookingReference(reference.trim());
    }

    @Override
    public Booking saveBooking(Booking booking) {
        return bookingRepository.save(booking);
    }

    @Override
    @Transactional
    public Booking createBooking(BookingRequestDto dto, String username) {
        if (dto == null) {
            throw new BookingException("Booking request data cannot be null");
        }
        if (username == null || username.isBlank()) {
            throw new BookingException("Authenticated guest username is required");
        }

        // Validate and parse dates
        LocalDate checkIn = dto.getCheckInDate();
        LocalDate checkOut = dto.getCheckOutDate();

        if (checkIn == null || checkOut == null) {
            throw new BookingException("Please select check-in and check-out dates.");
        }

        LocalDate today = LocalDate.now();
        if (checkIn.isBefore(today)) {
            throw new BookingException("Check-in date cannot be in the past.");
        }
        if (!checkOut.isAfter(checkIn)) {
            throw new BookingException("Check-out date must be strictly after the check-in date.");
        }

        // Resolve Guest entity
        Guest guest = guestRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new BookingException("Guest account not found for username: " + username));

        // Resolve Room entity
        Long roomId = dto.getRoomId();
        if (roomId == null) {
            throw new BookingException("Room ID must be specified");
        }

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new BookingException("Room not found with ID: " + roomId));

        // Verify date availability
        if (!isRoomAvailable(roomId, checkIn, checkOut)) {
            throw new BookingException("Selected room " + room.getRoomNumber() +
                    " is unavailable for the selected dates (" + checkIn + " to " + checkOut + ")");
        }

        // Resolve Hotel property
        Hotel hotel = null;
        if (dto.getHotelId() != null) {
            hotel = hotelRepository.findById(dto.getHotelId()).orElse(null);
        }
        if (hotel == null) {
            hotel = hotelRepository.findAll().stream().findFirst().orElse(null);
        }

        // Price Calculation & Historical Lock with Dynamic Tariff Optimizer (Stage 5 Requirement 13)
        long nights = ChronoUnit.DAYS.between(checkIn, checkOut);
        if (nights <= 0) {
            nights = 1;
        }

        double nightlyTariff;
        if (room.getCurrentTariff() > 0) {
            nightlyTariff = room.getCurrentTariff();
        } else {
            nightlyTariff = dynamicTariffService.calculateDynamicTariff(room, checkIn, checkOut);
            room.setCurrentTariff(nightlyTariff);
            roomRepository.save(room);
        }
        double totalAmount = nights * nightlyTariff;

        // Generate unique reference
        String bookingReference = generateUniqueBookingReference();

        // Create booking record
        Booking booking = new Booking();
        booking.setBookingReference(bookingReference);
        booking.setGuest(guest);
        booking.setHotel(hotel);
        booking.setRoom(room);
        String resolvedRoomType = dto.getRoomType();
        if (resolvedRoomType == null || resolvedRoomType.isBlank()) {
            resolvedRoomType = (room != null && room.getCategory() != null) ? room.getCategory().getDisplayName() : "Private / Single Room";
        }
        booking.setRoomType(resolvedRoomType.trim());
        booking.setCheckInDate(checkIn);
        if (dto.getCheckInTime() != null) {
            booking.setCheckInTime(dto.getCheckInTime());
        } else {
            booking.setCheckInTime(java.time.LocalTime.of(14, 0));
        }
        booking.setCheckOutDate(checkOut);
        booking.setNumberOfGuests(dto.getNumberOfGuests() > 0 ? dto.getNumberOfGuests() : 1);
        booking.setTotalAmount(totalAmount);
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setSpecialRequests(dto.getSpecialRequests());
        booking.setBookingDate(LocalDateTime.now());

        if (dto.getGuestDetails() != null && !dto.getGuestDetails().isEmpty()) {
            List<com.hotel.model.BookingGuestDetail> details = new ArrayList<>();
            int count = booking.getNumberOfGuests();
            for (int i = 0; i < count && i < dto.getGuestDetails().size(); i++) {
                com.hotel.model.BookingGuestDetail gd = dto.getGuestDetails().get(i);
                if (gd != null) {
                    details.add(new com.hotel.model.BookingGuestDetail(
                            (gd.getName() != null && !gd.getName().isBlank()) ? gd.getName().trim() : "",
                            (gd.getGuestType() != null && !gd.getGuestType().isBlank()) ? gd.getGuestType().trim() : "Adult",
                            gd.getAge() != null ? gd.getAge() : 30,
                            (gd.getGender() != null && !gd.getGender().isBlank()) ? gd.getGender().trim() : "Male"
                    ));
                }
            }
            booking.setGuestDetails(details);
        }

        // Update room status if check-in is today
        if (checkIn.equals(LocalDate.now())) {
            room.setStatus(RoomStatus.OCCUPIED);
            roomRepository.save(room);
        }

        Booking saved = bookingRepository.save(booking);
        log.info("Booking created: Reference={}, Guest={}, Hotel={}, Room={}, Dates={} to {}, Total=₹{}",
                saved.getBookingReference(), guest.getUsername(),
                (hotel != null ? hotel.getName() : "N/A"),
                room.getRoomNumber(), checkIn, checkOut, totalAmount);

        // Dispatch notifications to Guest, Staff, and Admins in pure Java
        try {
            notificationService.notifyBookingCreated(saved);
        } catch (Exception ex) {
            log.warn("Notification dispatch failed for booking {}: {}", saved.getBookingReference(), ex.getMessage());
        }

        return saved;
    }

    @Override
    @Transactional
    public boolean isRoomAvailable(Long roomId, LocalDate checkInDate, LocalDate checkOutDate) {
        if (roomId == null || checkInDate == null || checkOutDate == null) {
            return false;
        }
        if (!checkOutDate.isAfter(checkInDate)) {
            return false;
        }

        // First progress any expired bookings so completed stays do not block new reservations
        processAutomaticStatusTransitions();

        Optional<Room> roomOpt = roomRepository.findById(roomId);
        if (roomOpt.isEmpty()) {
            return false;
        }
        Room room = roomOpt.get();
        if (room.getStatus() == RoomStatus.MAINTENANCE) {
            return false;
        }

        // Query overlapping active bookings
        List<BookingStatus> activeStatuses = List.of(
                BookingStatus.CONFIRMED,
                BookingStatus.CHECKED_IN,
                BookingStatus.PENDING
        );

        List<Booking> overlaps = bookingRepository.findOverlappingBookings(roomId, activeStatuses, checkInDate, checkOutDate);
        return overlaps.isEmpty();
    }

    @Override
    public Booking updateBookingStatus(Long bookingId, BookingStatus newStatus) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingException("Booking not found with ID: " + bookingId));
        booking.setStatus(newStatus);
        Booking updated = bookingRepository.save(booking);
        if (booking.getRoom() != null) {
            updateRoomOccupancy(booking.getRoom());
        }
        return updated;
    }

    @Override
    public void cancelBooking(Long bookingId) {
        Booking booking = updateBookingStatus(bookingId, BookingStatus.CANCELLED);
        try {
            notificationService.notifyBookingCancelled(booking);
        } catch (Exception ex) {
            log.warn("Cancellation notification dispatch failed for booking {}: {}", bookingId, ex.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public long getTotalBookingCount() {
        return bookingRepository.count();
    }

    @Override
    @Transactional(readOnly = true)
    public double calculateDynamicTariff(Long roomId, LocalDate checkIn, LocalDate checkOut) {
        if (roomId == null) return 4500.0;
        Room room = roomRepository.findById(roomId).orElse(null);
        if (room == null) return 4500.0;
        return dynamicTariffService.calculateDynamicTariff(room, checkIn, checkOut);
    }

    /**
     * Automatic lifecycle progression (Stage 5 Requirement 8):
     * Runs every 60 seconds and also on demand before booking/query actions.
     * When current date passes check-out date:
     * CONFIRMED / CHECKED_IN -> COMPLETED
     * And frees room occupancy for future stays.
     */
    @Scheduled(fixedRate = 60000)
    @Override
    @Transactional
    public void processAutomaticStatusTransitions() {
        LocalDate today = LocalDate.now();
        List<Booking> activeBookings = bookingRepository.findByStatusIn(
                List.of(BookingStatus.CONFIRMED, BookingStatus.CHECKED_IN, BookingStatus.PENDING)
        );

        for (Booking b : activeBookings) {
            if (b.getCheckOutDate() != null && b.getCheckOutDate().isBefore(today)) {
                b.setStatus(BookingStatus.COMPLETED);
                bookingRepository.save(b);
                log.info("Auto-completed expired booking: {} (Checkout date {} has passed)",
                        b.getBookingReference(), b.getCheckOutDate());
                if (b.getRoom() != null) {
                    updateRoomOccupancy(b.getRoom());
                }
            } else if (b.getCheckInDate() != null && !b.getCheckInDate().isAfter(today)
                    && b.getStatus() == BookingStatus.CONFIRMED) {
                b.setStatus(BookingStatus.CHECKED_IN);
                bookingRepository.save(b);
                if (b.getRoom() != null && b.getRoom().getStatus() == RoomStatus.AVAILABLE) {
                    b.getRoom().setStatus(RoomStatus.OCCUPIED);
                    roomRepository.save(b.getRoom());
                }
            }
        }
    }

    private void updateRoomOccupancy(Room room) {
        if (room == null || room.getId() == null) return;
        LocalDate today = LocalDate.now();
        List<Booking> currentStays = bookingRepository.findOverlappingBookings(
                room.getId(),
                List.of(BookingStatus.CONFIRMED, BookingStatus.CHECKED_IN),
                today,
                today.plusDays(1)
        );
        if (currentStays.isEmpty() && room.getStatus() == RoomStatus.OCCUPIED) {
            room.setStatus(RoomStatus.AVAILABLE);
            roomRepository.save(room);
            log.info("Reverted room {} status to AVAILABLE as no active stay today", room.getRoomNumber());
        }
    }

    private synchronized String generateUniqueBookingReference() {
        int year = LocalDate.now().getYear();
        long nextNum = bookingRepository.count() + 1;
        String ref = String.format("BK-%d-%04d", year, nextNum);
        int attempts = 0;
        while (bookingRepository.existsByBookingReference(ref) && attempts < 1000) {
            nextNum++;
            attempts++;
            ref = String.format("BK-%d-%04d", year, nextNum);
        }
        return ref;
    }
}