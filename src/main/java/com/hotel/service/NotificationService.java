package com.hotel.service;

import com.hotel.model.Booking;
import com.hotel.model.Message;
import com.hotel.model.Notification;
import com.hotel.model.Room;
import com.hotel.model.User;
import com.hotel.model.enums.RoomStatus;

import java.util.List;

/**
 * Service defining notification dispatch, lifecycle, and reading operations
 * for Guest, Staff, and Admin users in pure Java.
 */
public interface NotificationService {

    Notification createNotification(User recipient, User sender, Message message,
                                   String subject, String preview, String notificationType);

    Notification createNotification(String recipientUsername, String senderUsername,
                                   String subject, String preview, String notificationType);

    void notifyBookingCreated(Booking booking);

    void notifyBookingCancelled(Booking booking);

    void notifyRoomStatusChange(Room room, RoomStatus oldStatus, RoomStatus newStatus, String staffUsername);

    List<Notification> getNotificationsForUser(String username);

    long getUnreadCount(String username);

    void markAsRead(Long notificationId, String username);

    void markAllAsRead(String username);
}