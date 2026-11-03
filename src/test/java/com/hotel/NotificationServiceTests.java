package com.hotel;

import com.hotel.model.Booking;
import com.hotel.model.Guest;
import com.hotel.model.Notification;
import com.hotel.model.Room;
import com.hotel.model.enums.BookingStatus;
import com.hotel.model.enums.RoomCategory;
import com.hotel.model.enums.RoomStatus;
import com.hotel.repository.GuestRepository;
import com.hotel.repository.RoomRepository;
import com.hotel.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class NotificationServiceTests {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private GuestRepository guestRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Test
    @DisplayName("Verify Notification Creation and Retrieval in Pure Java")
    void testCreateAndRetrieveNotification() {
        Guest guest = guestRepository.findAll().stream().findFirst().orElse(null);
        assertNotNull(guest, "Guest must exist from seed data");

        Notification notif = notificationService.createNotification(
                guest.getUsername(),
                "GAGAN",
                "Test Subject",
                "Test Preview Content",
                "SYSTEM_TEST"
        );
        assertNotNull(notif);
        assertNotNull(notif.getId());
        assertEquals("Test Subject", notif.getSubject());
        assertFalse(notif.isRead());

        List<Notification> guestNotifs = notificationService.getNotificationsForUser(guest.getUsername());
        assertFalse(guestNotifs.isEmpty());
        assertTrue(guestNotifs.stream().anyMatch(n -> n.getId().equals(notif.getId())));

        // Mark as read
        notificationService.markAsRead(notif.getId(), guest.getUsername());
        Notification updated = notificationService.getNotificationsForUser(guest.getUsername()).stream()
                .filter(n -> n.getId().equals(notif.getId()))
                .findFirst()
                .orElse(null);
        assertNotNull(updated);
        assertTrue(updated.isRead());
    }

    @Test
    @DisplayName("Verify Booking Created Notification Dispatch")
    void testNotifyBookingCreated() {
        Guest guest = guestRepository.findAll().stream().findFirst().orElse(null);
        Room room = roomRepository.findAll().stream().findFirst().orElse(null);
        assertNotNull(guest);
        assertNotNull(room);

        Booking booking = new Booking();
        booking.setBookingReference("BK-TEST-NOTIF-01");
        booking.setGuest(guest);
        booking.setRoom(room);
        booking.setCheckInDate(LocalDate.now().plusDays(1));
        booking.setCheckOutDate(LocalDate.now().plusDays(3));
        booking.setTotalAmount(9000.0);
        booking.setStatus(BookingStatus.CONFIRMED);

        assertDoesNotThrow(() -> notificationService.notifyBookingCreated(booking));
        assertDoesNotThrow(() -> notificationService.notifyBookingCancelled(booking));
    }

    @Test
    @DisplayName("Verify Room Status Change Notification Dispatch")
    void testNotifyRoomStatusChange() {
        Room room = roomRepository.findAll().stream().findFirst().orElse(null);
        assertNotNull(room);

        assertDoesNotThrow(() -> notificationService.notifyRoomStatusChange(
                room, RoomStatus.AVAILABLE, RoomStatus.CLEANING, "StaffMember"
        ));
    }
}