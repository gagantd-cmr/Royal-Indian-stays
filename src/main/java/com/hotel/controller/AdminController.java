package com.hotel.controller;

import com.hotel.dto.AdminSendMessageDto;
import com.hotel.dto.StaffCreateDto;
import com.hotel.model.Admin;
import com.hotel.model.Guest;
import com.hotel.model.Message;
import com.hotel.model.Notification;
import com.hotel.model.Staff;
import com.hotel.model.enums.AccountStatus;
import com.hotel.service.*;
import com.hotel.service.DynamicTariffService;
import com.hotel.service.RoomService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Controller handling Admin management portal views and operations in Stage 2.
 * Strictly restricted to Role ADMIN by Spring Security.
 * Integrates transparent Demo Mode isolation to present realistic fictional data
 * during prototype evaluation without exposing private database records.
 */
@Controller
@RequestMapping("/admin")
public class AdminController {

    private final AdminService adminService;
    private final StaffService staffService;
    private final GuestService guestService;
    private final InternalCommunicationService internalCommunicationService;
    private final BookingService bookingService;
    private final AdminOperationsService adminOperationsService;
    private final OfferService offerService;
    private final HotelService hotelService;
    private final DemoDataService demoDataService;
    private final RoomService roomService;
    private final DynamicTariffService dynamicTariffService;

    @Autowired
    public AdminController(AdminService adminService,
                           StaffService staffService,
                           GuestService guestService,
                           InternalCommunicationService internalCommunicationService,
                           BookingService bookingService,
                           AdminOperationsService adminOperationsService,
                           OfferService offerService,
                           HotelService hotelService,
                           DemoDataService demoDataService,
                           RoomService roomService,
                           DynamicTariffService dynamicTariffService) {
        this.adminService = adminService;
        this.staffService = staffService;
        this.guestService = guestService;
        this.internalCommunicationService = internalCommunicationService;
        this.bookingService = bookingService;
        this.adminOperationsService = adminOperationsService;
        this.offerService = offerService;
        this.hotelService = hotelService;
        this.demoDataService = demoDataService;
        this.roomService = roomService;
        this.dynamicTariffService = dynamicTariffService;
    }

    @ModelAttribute("unreadNotificationCount")
    public long populateUnreadNotifications(Principal principal, HttpSession session) {
        if (demoDataService.isDemoMode(session)) {
            return demoDataService.getAdminNotifications(session).size();
        }
        if (principal != null) {
            return internalCommunicationService.getUnreadNotificationCount(principal.getName());
        }
        return 0;
    }

    @GetMapping({"", "/", "/home"})
    public String adminHomeRedirect() {
        return "redirect:/";
    }

    /**
     * Admin Dashboard: High-Level Hotel Operations Control Center (Requirement 6)
     */
    @GetMapping("/dashboard")
    public String dashboard(Model model, Principal principal, HttpSession session) {
        model.addAttribute("activeNav", "dashboard");

        if (demoDataService.isDemoMode(session)) {
            model.addAttribute("adminUsername", "Demo Admin");
            model.addAttribute("guestActivity", demoDataService.getAdminGuestActivityOverview(session));
            model.addAttribute("hotelOperations", demoDataService.getAdminHotelOperationsOverview(session));
            model.addAttribute("maintenance", demoDataService.getAdminMaintenanceOverview(session));
            model.addAttribute("inspections", demoDataService.getAdminInspectionsOverview(session));
            model.addAttribute("staffActivity", demoDataService.getAdminStaffActivityOverview(session));
            model.addAttribute("maintenanceHotels", demoDataService.getAdminMaintenanceAffectedHotels(session));
            model.addAttribute("unreadNotificationCount", demoDataService.getAdminNotifications(session).size());
            return "admin/dashboard";
        }

        String adminUsername = principal != null ? principal.getName() : "ADMIN";
        model.addAttribute("adminUsername", adminUsername);

        // Core operational metrics (Level 1 Status & Percentages)
        model.addAttribute("guestActivity", adminOperationsService.getGuestActivityOverview());
        model.addAttribute("hotelOperations", adminOperationsService.getHotelOperationsOverview());
        model.addAttribute("maintenance", adminOperationsService.getMaintenanceOverview());
        model.addAttribute("inspections", adminOperationsService.getInspectionsOverview());
        model.addAttribute("staffActivity", adminOperationsService.getStaffActivityOverview());

        // Level 2 pre-computed details for initial seamless click interaction
        model.addAttribute("maintenanceHotels", adminOperationsService.getMaintenanceAffectedHotels());

        long unreadCount = internalCommunicationService.getUnreadNotificationCount(adminUsername);
        model.addAttribute("unreadNotificationCount", unreadCount);

        return "admin/dashboard";
    }

    /**
     * Check Hotels: Admin Operational Overview for Hotel/Room Availability (Requirement 3)
     */
    @GetMapping("/check-hotels")
    public String checkHotels(Model model, HttpSession session) {
        model.addAttribute("activeNav", "check-hotels");

        if (demoDataService.isDemoMode(session)) {
            model.addAttribute("operations", demoDataService.getAdminHotelOperationsOverview(session));
            model.addAttribute("availableRooms", demoDataService.getAdminRoomsByStatus(session, "AVAILABLE"));
            model.addAttribute("reservedRooms", demoDataService.getAdminRoomsByStatus(session, "RESERVED"));
            model.addAttribute("prebookedRooms", demoDataService.getAdminRoomsByStatus(session, "PRE-BOOKED"));
            model.addAttribute("occupiedRooms", demoDataService.getAdminRoomsByStatus(session, "OCCUPIED"));
            model.addAttribute("currentGuestsList", demoDataService.getAdminGuestFlow(session, "CURRENT"));
            model.addAttribute("checkInsList", demoDataService.getAdminGuestFlow(session, "CHECKINS"));
            model.addAttribute("checkOutsList", demoDataService.getAdminGuestFlow(session, "CHECKOUTS"));
            return "admin/check-hotels";
        }

        Map<String, Object> ops = adminOperationsService.getHotelOperationsOverview();
        model.addAttribute("operations", ops);

        // Preload operational status details for Level 2 click interaction
        model.addAttribute("availableRooms", adminOperationsService.getRoomsByOperationalStatus("AVAILABLE"));
        model.addAttribute("reservedRooms", adminOperationsService.getRoomsByOperationalStatus("RESERVED"));
        model.addAttribute("prebookedRooms", adminOperationsService.getRoomsByOperationalStatus("PRE-BOOKED"));
        model.addAttribute("occupiedRooms", adminOperationsService.getRoomsByOperationalStatus("OCCUPIED"));

        model.addAttribute("currentGuestsList", adminOperationsService.getGuestFlowDetails("CURRENT"));
        model.addAttribute("checkInsList", adminOperationsService.getGuestFlowDetails("CHECKINS"));
        model.addAttribute("checkOutsList", adminOperationsService.getGuestFlowDetails("CHECKOUTS"));

        return "admin/check-hotels";
    }

    /**
     * Districts: Hierarchical State -> District -> Hotel Operational Overview (Requirement 4)
     */
    @GetMapping("/districts")
    public String districtsHierarchy(@RequestParam(value = "state", required = false) String state,
                                     @RequestParam(value = "district", required = false) String district,
                                     Model model,
                                     HttpSession session) {
        model.addAttribute("activeNav", "districts");

        List<String> states = hotelService.getAllStates();
        model.addAttribute("states", states);

        String activeState = (state != null && !state.isBlank()) ? state.trim() : (states.isEmpty() ? "Karnataka" : states.get(0));
        model.addAttribute("selectedState", activeState);

        List<String> districts = hotelService.getDistrictsByState(activeState);
        model.addAttribute("districts", districts);

        String activeDistrict = (district != null && !district.isBlank()) ? district.trim() : (districts.isEmpty() ? null : districts.get(0));
        model.addAttribute("selectedDistrict", activeDistrict);

        if (activeDistrict != null) {
            if (demoDataService.isDemoMode(session)) {
                model.addAttribute("districtOperations", demoDataService.getAdminDistrictOperationsOverview(session, activeState, activeDistrict));
            } else {
                Map<String, Object> districtOps = adminOperationsService.getDistrictOperationsOverview(activeState, activeDistrict);
                model.addAttribute("districtOperations", districtOps);
            }

            List<com.hotel.model.Hotel> districtHotels = hotelService.getHotelsByDistrict(activeState, activeDistrict);
            model.addAttribute("districtHotels", districtHotels);
        }

        return "admin/districts";
    }

    /**
     * Offers Management: View, Create, and Edit Promotions (Requirement 5)
     */
    @GetMapping("/offers")
    public String adminOffers(Model model, HttpSession session) {
        model.addAttribute("activeNav", "offers");
        if (demoDataService.isDemoMode(session)) {
            model.addAttribute("offers", demoDataService.getDemoOffers(session));
            return "admin/offers";
        }
        model.addAttribute("offers", offerService.getAllOffers());
        return "admin/offers";
    }

    @PostMapping("/offers/create")
    public String createOffer(@ModelAttribute com.hotel.model.Offer offer, RedirectAttributes redirectAttributes, HttpSession session) {
        if (demoDataService.isDemoMode(session)) {
            demoDataService.saveDemoOffer(session, offer);
            redirectAttributes.addFlashAttribute("successMessage", "Demo offer '" + (offer.getTitle() != null ? offer.getTitle() : "Luxury Promotion") + "' created successfully in Demo Mode.");
            return "redirect:/admin/offers";
        }
        if (offer.getTitle() != null && !offer.getTitle().isBlank()) {
            offerService.saveOffer(offer);
            redirectAttributes.addFlashAttribute("successMessage", "Luxury offer '" + offer.getTitle() + "' successfully created.");
        }
        return "redirect:/admin/offers";
    }

    @PostMapping("/offers/{id}/edit")
    public String editOffer(@PathVariable Long id, @ModelAttribute com.hotel.model.Offer offerData, RedirectAttributes redirectAttributes, HttpSession session) {
        if (demoDataService.isDemoMode(session)) {
            offerData.setId(id);
            demoDataService.saveDemoOffer(session, offerData);
            redirectAttributes.addFlashAttribute("successMessage", "Demo offer updated successfully in Demo Mode.");
            return "redirect:/admin/offers";
        }
        Optional<com.hotel.model.Offer> existingOpt = offerService.getOfferById(id);
        if (existingOpt.isPresent()) {
            com.hotel.model.Offer o = existingOpt.get();
            o.setTitle(offerData.getTitle());
            o.setTag(offerData.getTag());
            o.setSubtitle(offerData.getSubtitle());
            o.setPerks(offerData.getPerks());
            o.setPromoCode(offerData.getPromoCode());
            o.setDiscountPercentage(offerData.getDiscountPercentage());
            o.setTargetDistrict(offerData.getTargetDistrict());
            o.setTargetUrl(offerData.getTargetUrl());
            o.setActive(offerData.isActive());
            offerService.saveOffer(o);
            redirectAttributes.addFlashAttribute("successMessage", "Offer '" + o.getTitle() + "' updated successfully.");
        }
        return "redirect:/admin/offers";
    }

    @PostMapping("/offers/{id}/toggle-status")
    public String toggleOfferStatus(@PathVariable Long id, RedirectAttributes redirectAttributes, HttpSession session) {
        if (demoDataService.isDemoMode(session)) {
            demoDataService.toggleDemoOfferStatus(session, id);
            redirectAttributes.addFlashAttribute("successMessage", "Demo offer status toggled successfully in Demo Mode.");
            return "redirect:/admin/offers";
        }
        com.hotel.model.Offer updated = offerService.toggleOfferStatus(id);
        if (updated != null) {
            String status = updated.isActive() ? "Activated" : "Deactivated";
            redirectAttributes.addFlashAttribute("successMessage", "Offer status changed to " + status);
        }
        return "redirect:/admin/offers";
    }

    @PostMapping("/offers/{id}/delete")
    public String deleteOffer(@PathVariable Long id, RedirectAttributes redirectAttributes, HttpSession session) {
        if (demoDataService.isDemoMode(session)) {
            demoDataService.deleteDemoOffer(session, id);
            redirectAttributes.addFlashAttribute("successMessage", "Demo offer removed from Demo Mode.");
            return "redirect:/admin/offers";
        }
        offerService.deleteOffer(id);
        redirectAttributes.addFlashAttribute("successMessage", "Offer deleted successfully.");
        return "redirect:/admin/offers";
    }

    // =========================================================================
    // REST API ENDPOINTS FOR DYNAMIC LEVEL 2 & LEVEL 3 DRILL-DOWN (ADMIN)
    // =========================================================================

    @GetMapping("/api/operations/rooms")
    @ResponseBody
    public List<Map<String, Object>> getRoomsByStatus(@RequestParam(value = "status", defaultValue = "AVAILABLE") String status,
                                                      HttpSession session) {
        if (demoDataService.isDemoMode(session)) {
            return demoDataService.getAdminRoomsByStatus(session, status);
        }
        return adminOperationsService.getRoomsByOperationalStatus(status);
    }

    @GetMapping("/api/operations/guest-flow")
    @ResponseBody
    public List<Map<String, Object>> getGuestFlow(@RequestParam(value = "type", defaultValue = "CURRENT") String type,
                                                  HttpSession session) {
        if (demoDataService.isDemoMode(session)) {
            return demoDataService.getAdminGuestFlow(session, type);
        }
        return adminOperationsService.getGuestFlowDetails(type);
    }

    @GetMapping("/api/operations/guest-activity")
    @ResponseBody
    public List<Map<String, Object>> getGuestActivity(@RequestParam(value = "category", defaultValue = "CURRENT") String category,
                                                      HttpSession session) {
        if (demoDataService.isDemoMode(session)) {
            return demoDataService.getAdminGuestActivityDetails(session, category);
        }
        return adminOperationsService.getGuestActivityDetails(category);
    }

    @GetMapping("/api/operations/hotel/{id}")
    @ResponseBody
    public Map<String, Object> getHotelOperationalDetails(@PathVariable Long id,
                                                          HttpSession session) {
        if (demoDataService.isDemoMode(session)) {
            return demoDataService.getAdminHotelOperationalDetails(session, id);
        }
        return adminOperationsService.getHotelOperationalDetails(id);
    }

    @GetMapping("/api/operations/maintenance/records")
    @ResponseBody
    public List<com.hotel.model.MaintenanceRecord> getMaintenanceRecordsForHotel(@RequestParam("hotelId") Long hotelId,
                                                                                HttpSession session) {
        if (demoDataService.isDemoMode(session)) {
            return demoDataService.getAdminMaintenanceRecords(session, hotelId);
        }
        return adminOperationsService.getMaintenanceRecordsForHotel(hotelId);
    }

    @GetMapping("/api/operations/inspections")
    @ResponseBody
    public List<com.hotel.model.Inspection> getInspections(@RequestParam(value = "status", defaultValue = "ALL") String status,
                                                          HttpSession session) {
        if (demoDataService.isDemoMode(session)) {
            return demoDataService.getAdminInspections(session, status);
        }
        return adminOperationsService.getInspectionsByStatus(status);
    }

    @GetMapping("/api/operations/staff")
    @ResponseBody
    public List<com.hotel.model.Staff> getStaffByActivityStatus(@RequestParam(value = "status", defaultValue = "ALL") String status,
                                                              HttpSession session) {
        if (demoDataService.isDemoMode(session)) {
            return demoDataService.getAdminStaffByActivityStatus(session, status);
        }
        return adminOperationsService.getStaffByActivityStatus(status);
    }

    /**
     * Staff Directory (STAFF LIST)
     */
    @GetMapping("/staff")
    public String staffList(Model model, HttpSession session) {
        model.addAttribute("activeNav", "staff");
        if (demoDataService.isDemoMode(session)) {
            model.addAttribute("staffList", demoDataService.getAdminStaffList(session));
            return "admin/staff";
        }
        model.addAttribute("staffList", staffService.getAllStaff());
        return "admin/staff";
    }

    /**
     * Display Add Staff Form
     */
    @GetMapping("/staff/add")
    public String addStaffForm(Model model) {
        model.addAttribute("activeNav", "add-staff");
        model.addAttribute("staffDto", new StaffCreateDto());
        return "admin/add-staff";
    }

    /**
     * Process Add Staff Form Submission
     */
    @PostMapping("/staff/add")
    public String processAddStaff(@Valid @ModelAttribute("staffDto") StaffCreateDto staffDto,
                                  BindingResult bindingResult,
                                  Model model,
                                  RedirectAttributes redirectAttributes,
                                  HttpSession session) {
        if (demoDataService.isDemoMode(session)) {
            demoDataService.addDemoStaff(session, staffDto);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Demo staff account for '" + staffDto.getStaffName() + "' created and active in demo session.");
            return "redirect:/admin/staff";
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("activeNav", "add-staff");
            return "admin/add-staff";
        }

        if (!staffDto.isPasswordMatching()) {
            bindingResult.rejectValue("confirmPassword", "error.staffDto", "Password and Confirm Password do not match.");
            model.addAttribute("activeNav", "add-staff");
            return "admin/add-staff";
        }

        if (staffService.existsByUsername(staffDto.getStaffName())) {
            bindingResult.rejectValue("staffName", "error.staffDto", "Staff member with name '" + staffDto.getStaffName() + "' already exists.");
            model.addAttribute("activeNav", "add-staff");
            return "admin/add-staff";
        }

        try {
            Staff savedStaff = staffService.createStaff(staffDto);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Staff account for '" + savedStaff.getUsername() + "' successfully created and active!");
            return "redirect:/admin/staff";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("activeNav", "add-staff");
            return "admin/add-staff";
        }
    }

    /**
     * Toggle Staff Account Status (Active / Inactive)
     */
    @PostMapping("/staff/{id}/toggle-status")
    public String toggleStaffStatus(@PathVariable Long id, RedirectAttributes redirectAttributes, HttpSession session) {
        if (demoDataService.isDemoMode(session)) {
            demoDataService.toggleDemoStaffStatus(session, id);
            redirectAttributes.addFlashAttribute("successMessage", "Demo staff status toggled successfully in Demo Mode.");
            return "redirect:/admin/staff";
        }
        Optional<Staff> staffOpt = staffService.getStaffById(id);
        if (staffOpt.isPresent()) {
            Staff staff = staffOpt.get();
            boolean newStatusActive = !(staff.getStatus() == AccountStatus.ACTIVE && staff.isActive());
            staff.setStatus(newStatusActive ? AccountStatus.ACTIVE : AccountStatus.INACTIVE);
            staff.setActive(newStatusActive);
            staffService.saveStaff(staff);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Staff member '" + staff.getUsername() + "' status changed to " + staff.getStatus().getDisplayName());
        }
        return "redirect:/admin/staff";
    }

    /**
     * Guest Directory (GUEST LIST)
     */
    @GetMapping("/guests")
    public String guestList(Model model, HttpSession session) {
        model.addAttribute("activeNav", "guests");
        if (demoDataService.isDemoMode(session)) {
            model.addAttribute("guestList", demoDataService.getAdminGuestList(session));
            return "admin/guests";
        }
        model.addAttribute("guestList", guestService.getAllGuests());
        return "admin/guests";
    }

    /**
     * Centralized Oversight of All Bookings (ADMIN BOOKINGS)
     */
    @GetMapping("/bookings")
    public String bookings(Model model, HttpSession session) {
        model.addAttribute("activeNav", "bookings");
        if (demoDataService.isDemoMode(session)) {
            model.addAttribute("bookings", demoDataService.getAdminBookingsList(session));
            return "admin/bookings";
        }
        model.addAttribute("bookings", bookingService.getAllBookings());
        return "admin/bookings";
    }

    /**
     * Luxury Room & Suite Inventory (ADMIN ROOMS)
     */
    @GetMapping("/rooms")
    public String rooms(Model model, HttpSession session) {
        model.addAttribute("activeNav", "rooms");
        if (demoDataService.isDemoMode(session)) {
            model.addAttribute("rooms", demoDataService.getStaffRoomsList(session));
            return "admin/rooms";
        }
        model.addAttribute("rooms", roomService.getAllRooms());
        return "admin/rooms";
    }

    /**
     * Dynamic Tariff Analytics & Revenue Reports (ADMIN REPORTS)
     */
    @GetMapping("/reports")
    public String reports(Model model, HttpSession session) {
        model.addAttribute("activeNav", "reports");
        Map<String, Object> tariffAnalytics = dynamicTariffService.getDynamicTariffAnalytics();
        model.addAllAttributes(tariffAnalytics);
        model.addAttribute("analytics", tariffAnalytics);
        return "admin/reports";
    }

    @GetMapping("/api/operations/dynamic-tariff")
    @ResponseBody
    public Map<String, Object> getDynamicTariffApi() {
        return dynamicTariffService.getDynamicTariffAnalytics();
    }

    /**
     * Admin Profile
     */
    @GetMapping("/profile")
    public String adminProfile(Model model, Principal principal, HttpSession session) {
        model.addAttribute("activeNav", "profile");
        if (demoDataService.isDemoMode(session)) {
            model.addAttribute("admin", demoDataService.getAdminProfile(session));
            return "admin/profile";
        }
        String username = principal != null ? principal.getName() : "ADMIN";
        Admin admin = adminService.getAdminByUsername(username)
                .orElse(new Admin(username, ""));
        model.addAttribute("admin", admin);
        return "admin/profile";
    }

    // =========================================================================
    // INTERNAL COMMUNICATION & NOTIFICATION ENDPOINTS (ADMIN)
    // =========================================================================

    @GetMapping("/notifications")
    public String notifications(Model model, Principal principal, HttpSession session) {
        model.addAttribute("activeNav", "notifications");
        if (demoDataService.isDemoMode(session)) {
            model.addAttribute("notifications", demoDataService.getAdminNotifications(session));
            return "admin/notifications";
        }
        String username = principal != null ? principal.getName() : "ADMIN";
        model.addAttribute("notifications", internalCommunicationService.getNotificationsForUser(username));
        return "admin/notifications";
    }

    @PostMapping("/notifications/mark-all-read")
    public String markAllNotificationsRead(Principal principal, RedirectAttributes redirectAttributes, HttpSession session) {
        if (demoDataService.isDemoMode(session)) {
            demoDataService.markAllDemoAdminNotificationsRead(session);
            redirectAttributes.addFlashAttribute("successMessage", "All demo notifications marked as read.");
            return "redirect:/admin/notifications";
        }
        if (principal != null) {
            internalCommunicationService.markAllNotificationsAsRead(principal.getName());
            redirectAttributes.addFlashAttribute("successMessage", "All notifications marked as read.");
        }
        return "redirect:/admin/notifications";
    }

    @GetMapping("/notifications/{id}")
    public String viewNotification(@PathVariable Long id, Principal principal, HttpSession session) {
        if (demoDataService.isDemoMode(session)) {
            return "redirect:/admin/messages/requests";
        }
        String username = principal != null ? principal.getName() : "ADMIN";
        try {
            Notification notif = internalCommunicationService.getNotificationForUser(id, username);
            if (notif.getMessage() != null) {
                return "redirect:/admin/messages/" + notif.getMessage().getId();
            }
            return "redirect:/admin/notifications";
        } catch (org.springframework.security.access.AccessDeniedException ex) {
            return "redirect:/access-denied";
        } catch (IllegalArgumentException ex) {
            return "redirect:/admin/notifications";
        }
    }

    @GetMapping("/messages")
    public String messagesOverview() {
        return "redirect:/admin/messages/inbox";
    }

    @GetMapping("/messages/inbox")
    public String messagesInbox(Model model, Principal principal, HttpSession session) {
        model.addAttribute("activeNav", "messages");
        model.addAttribute("subTab", "inbox");
        if (demoDataService.isDemoMode(session)) {
            model.addAttribute("messages", demoDataService.getAdminInboxMessages(session));
            model.addAttribute("unreadMessageIds", Set.of(701L));
            return "admin/messages-inbox";
        }
        String username = principal != null ? principal.getName() : "ADMIN";
        model.addAttribute("messages", internalCommunicationService.getInboxMessages(username));
        model.addAttribute("unreadMessageIds", internalCommunicationService.getUnreadMessageIdsForUser(username));
        return "admin/messages-inbox";
    }

    @GetMapping("/messages/sent")
    public String messagesSent(Model model, Principal principal, HttpSession session) {
        model.addAttribute("activeNav", "messages");
        model.addAttribute("subTab", "sent");
        if (demoDataService.isDemoMode(session)) {
            model.addAttribute("messages", demoDataService.getAdminSentMessages(session));
            return "admin/messages-sent";
        }
        String username = principal != null ? principal.getName() : "ADMIN";
        model.addAttribute("messages", internalCommunicationService.getSentMessages(username));
        return "admin/messages-sent";
    }

    @GetMapping("/messages/requests")
    public String messagesRequests(Model model, Principal principal, HttpSession session) {
        model.addAttribute("activeNav", "requests");
        model.addAttribute("subTab", "requests");
        if (demoDataService.isDemoMode(session)) {
            model.addAttribute("messages", demoDataService.getAdminStaffRequests(session));
            model.addAttribute("unreadMessageIds", Set.of(701L));
            return "admin/messages-requests";
        }
        String username = principal != null ? principal.getName() : "ADMIN";
        model.addAttribute("messages", internalCommunicationService.getStaffRequestsForAdmin(username));
        model.addAttribute("unreadMessageIds", internalCommunicationService.getUnreadMessageIdsForUser(username));
        return "admin/messages-requests";
    }

    @GetMapping("/messages/new")
    public String newMessageForm(@RequestParam(value = "recipientId", required = false) Long recipientId,
                                 Model model,
                                 HttpSession session) {
        model.addAttribute("activeNav", "messages");
        model.addAttribute("subTab", "new");
        if (demoDataService.isDemoMode(session)) {
            model.addAttribute("staffList", demoDataService.getAdminStaffList(session));
        } else {
            model.addAttribute("staffList", staffService.getAllStaff());
        }

        if (!model.containsAttribute("messageDto")) {
            AdminSendMessageDto dto = new AdminSendMessageDto();
            if (recipientId != null) {
                dto.getStaffRecipientIds().add(recipientId);
            }
            model.addAttribute("messageDto", dto);
        }
        return "admin/messages-new";
    }

    @PostMapping("/messages/new")
    public String processSendMessage(@Valid @ModelAttribute("messageDto") AdminSendMessageDto messageDto,
                                     BindingResult bindingResult,
                                     Principal principal,
                                     RedirectAttributes redirectAttributes,
                                     Model model,
                                     HttpSession session) {
        if (demoDataService.isDemoMode(session)) {
            demoDataService.sendDemoAdminMessage(session, messageDto.getStaffRecipientIds(), messageDto.getSubject(), messageDto.getContent());
            redirectAttributes.addFlashAttribute("successMessage", "Demo broadcast sent successfully to operational staff.");
            return "redirect:/admin/messages/sent";
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("activeNav", "messages");
            model.addAttribute("subTab", "new");
            model.addAttribute("staffList", staffService.getAllStaff());
            return "admin/messages-new";
        }

        String username = principal != null ? principal.getName() : "ADMIN";
        try {
            internalCommunicationService.sendAdminMessage(
                    username,
                    messageDto.getStaffRecipientIds(),
                    messageDto.getSubject(),
                    messageDto.getContent()
            );
            redirectAttributes.addFlashAttribute("successMessage", "Message sent successfully.");
            return "redirect:/admin/messages/sent";
        } catch (IllegalArgumentException ex) {
            bindingResult.reject("error.messageDto", ex.getMessage());
            model.addAttribute("activeNav", "messages");
            model.addAttribute("subTab", "new");
            model.addAttribute("staffList", staffService.getAllStaff());
            return "admin/messages-new";
        }
    }

    @GetMapping("/messages/{id}")
    public String viewMessage(@PathVariable Long id, Model model, Principal principal, HttpSession session) {
        model.addAttribute("activeNav", "messages");
        if (demoDataService.isDemoMode(session)) {
            model.addAttribute("messageItem", demoDataService.getAdminMessageById(session, id));
            model.addAttribute("currentUser", "Demo Admin");
            return "admin/message-view";
        }
        String username = principal != null ? principal.getName() : "ADMIN";
        try {
            Message message = internalCommunicationService.getMessageForUser(id, username);
            model.addAttribute("messageItem", message);
            model.addAttribute("currentUser", username);
            return "admin/message-view";
        } catch (org.springframework.security.access.AccessDeniedException ex) {
            return "redirect:/access-denied";
        } catch (IllegalArgumentException ex) {
            return "redirect:/admin/messages/inbox";
        }
    }
}
