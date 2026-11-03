package com.hotel.service.impl;

import com.hotel.model.*;
import com.hotel.model.enums.RoomStatus;
import com.hotel.repository.AdminRepository;
import com.hotel.repository.NotificationRepository;
import com.hotel.repository.StaffRepository;
import com.hotel.repository.UserRepository;
import com.hotel.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class NotificationServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final AdminRepository adminRepository;
    private final StaffRepository staffRepository;

    @Autowired
    public NotificationServiceImpl(NotificationRepository notificationRepository,
                                   UserRepository userRepository,
                                   AdminRepository adminRepository,
                                   StaffRepository staffRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.adminRepository = adminRepository;
        this.staffRepository = staffRepository;
    }

    @Override
    public Notification createNotification(User recipient, User sender, Message message,
                                           String subject, String preview, String notificationType) {
        if (recipient == null) {
            return null;
        }
        User actualSender = sender != null ? sender : getSystemAdminUser();
        Notification notification = new Notification(recipient, actualSender, message, subject, preview, notificationType);
        return notificationRepository.save(notification);
    }

    @Override
    public Notification createNotification(String recipientUsername, String senderUsername,
                                           String subject, String preview, String notificationType) {
        Optional<User> recipientOpt = userRepository.findByUsername(recipientUsername);
        if (recipientOpt.isEmpty()) {
            return null;
        }
        User sender = null;
        if (senderUsername != null) {
            sender = userRepository.findByUsername(senderUsername).orElse(null);
        }
        return createNotification(recipientOpt.get(), sender, null, subject, preview, notificationType);
    }

    @Override
    public void notifyBookingCreated(Booking booking) {
        if (booking == null) return;
        User systemAdmin = getSystemAdminUser();
        String hotelName = booking.getHotel() != null ? booking.getHotel().getName() : "Royal Indian Stays Luxury Property";
        String roomNumber = booking.getRoom() != null ? booking.getRoom().getRoomNumber() : "Suite";
        String guestName = booking.getGuest() != null ? booking.getGuest().getFullName() : "Guest";

        // 1. Notify Guest
        if (booking.getGuest() != null) {
            createNotification(
                    booking.getGuest(),
                    systemAdmin,
                    null,
                    "Reservation Confirmed: " + booking.getBookingReference(),
                    "Your luxury stay at " + hotelName + " (Room " + roomNumber + ") from "
                            + booking.getCheckInDate() + " to " + booking.getCheckOutDate()
                            + " is successfully confirmed. Total locked tariff: ₹" + booking.getTotalAmount(),
                    "BOOKING_CONFIRMATION"
            );
        }

        // 2. Notify all Admins
        List<Admin> admins = adminRepository.findAll();
        for (Admin a : admins) {
            createNotification(
                    a,
                    booking.getGuest() != null ? booking.getGuest() : systemAdmin,
                    null,
                    "New Booking: " + booking.getBookingReference(),
                    "Guest " + guestName + " confirmed reservation at " + hotelName + " for Room "
                            + roomNumber + " (Total: ₹" + booking.getTotalAmount() + ").",
                    "BOOKING_NEW"
            );
        }

        // 3. Notify Staff
        List<Staff> staffMembers = staffRepository.findAll();
        for (Staff s : staffMembers) {
            createNotification(
                    s,
                    systemAdmin,
                    null,
                    "Housekeeping Notice: Room " + roomNumber,
                    "Upcoming arrival for " + guestName + " at " + hotelName + " on "
                            + booking.getCheckInDate() + ". Prepare room turn-down.",
                    "HOUSEKEEPING_SCHEDULE"
            );
        }
        log.info("Dispatched booking creation notifications for {}", booking.getBookingReference());
    }

    @Override
    public void notifyBookingCancelled(Booking booking) {
        if (booking == null) return;
        User systemAdmin = getSystemAdminUser();
        String hotelName = booking.getHotel() != null ? booking.getHotel().getName() : "Luxury Property";
        String guestName = booking.getGuest() != null ? booking.getGuest().getFullName() : "Guest";

        // Notify Guest
        if (booking.getGuest() != null) {
            createNotification(
                    booking.getGuest(),
                    systemAdmin,
                    null,
                    "Booking Cancelled: " + booking.getBookingReference(),
                    "Your booking " + booking.getBookingReference() + " for " + hotelName
                            + " has been cancelled. Inventory released.",
                    "BOOKING_CANCELLED"
            );
        }

        // Notify Admins
        List<Admin> admins = adminRepository.findAll();
        for (Admin a : admins) {
            createNotification(
                    a,
                    booking.getGuest() != null ? booking.getGuest() : systemAdmin,
                    null,
                    "Cancellation Alert: " + booking.getBookingReference(),
                    "Guest " + guestName + " has cancelled booking " + booking.getBookingReference()
                            + " at " + hotelName + ".",
                    "BOOKING_CANCELLED"
            );
        }
    }

    @Override
    public void notifyRoomStatusChange(Room room, RoomStatus oldStatus, RoomStatus newStatus, String staffUsername) {
        if (room == null) return;
        User sender = null;
        if (staffUsername != null) {
            sender = userRepository.findByUsername(staffUsername).orElse(null);
        }
        if (sender == null) {
            sender = getSystemAdminUser();
        }

        List<Admin> admins = adminRepository.findAll();
        for (Admin a : admins) {
            createNotification(
                    a,
                    sender,
                    null,
                    "Room " + room.getRoomNumber() + " Status: " + newStatus.getDisplayName(),
                    "Room " + room.getRoomNumber() + " updated from " + (oldStatus != null ? oldStatus.getDisplayName() : "Initial")
                            + " to " + newStatus.getDisplayName() + " by " + (staffUsername != null ? staffUsername : "Staff") + ".",
                    "ROOM_STATUS_CHANGE"
            );
        }
    }

    @Override
    public List<Notification> getNotificationsForUser(String username) {
        return notificationRepository.findByRecipientUsernameOrderByCreatedAtDesc(username);
    }

    @Override
    public long getUnreadCount(String username) {
        return notificationRepository.countByRecipientUsernameAndIsReadFalse(username);
    }

    @Override
    public void markAsRead(Long notificationId, String username) {
        if (notificationId == null || username == null) return;
        Optional<Notification> notifOpt = notificationRepository.findByIdAndRecipientUsername(notificationId, username);
        if (notifOpt.isPresent()) {
            Notification n = notifOpt.get();
            n.setRead(true);
            n.setReadAt(LocalDateTime.now());
            notificationRepository.save(n);
        }
    }

    @Override
    public void markAllAsRead(String username) {
        if (username == null) return;
        List<Notification> unreadList = notificationRepository.findByRecipientUsernameOrderByCreatedAtDesc(username);
        for (Notification n : unreadList) {
            if (!n.isRead()) {
                n.setRead(true);
                n.setReadAt(LocalDateTime.now());
                notificationRepository.save(n);
            }
        }
    }

    private User getSystemAdminUser() {
        Optional<Admin> adminOpt = adminRepository.findAll().stream().findFirst();
        if (adminOpt.isPresent()) {
            return adminOpt.get();
        }
        Optional<User> gaganOpt = userRepository.findByUsername("GAGAN");
        return gaganOpt.orElse(null);
    }
}