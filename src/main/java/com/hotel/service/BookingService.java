package com.hotel.service;

import com.hotel.model.Booking;
import com.hotel.model.enums.BookingStatus;
import com.hotel.dto.BookingRequestDto;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Service interface for booking operations.
 */
public interface BookingService {
    List<Booking> getAllBookings();
    List<Booking> getBookingsByGuestId(Long guestId);
    List<Booking> getActiveBookingsByGuest(Long guestId);
    List<Booking> getPastBookingsByGuest(Long guestId);
    Optional<Booking> getBookingById(Long id);
    Optional<Booking> getBookingByReference(String reference);
    Booking saveBooking(Booking booking);
    Booking createBooking(BookingRequestDto dto, String username);
    boolean isRoomAvailable(Long roomId, LocalDate checkInDate, LocalDate checkOutDate);
    Booking updateBookingStatus(Long bookingId, BookingStatus newStatus);
    void cancelBooking(Long bookingId);
    long getTotalBookingCount();
    void processAutomaticStatusTransitions();
    double calculateDynamicTariff(Long roomId, LocalDate checkIn, LocalDate checkOut);
}