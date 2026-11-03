package com.hotel.controller;

import com.hotel.model.Booking;
import com.hotel.model.Guest;
import com.hotel.model.Hotel;
import com.hotel.model.PlannedHotel;
import com.hotel.service.*;
import com.hotel.matcher.HotelMatchResult;
import com.hotel.matcher.PreferenceCriteria;
import com.hotel.matcher.PreferenceMatcher;
import com.hotel.model.GuestPreference;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.LocalDate;
import java.util.*;

/**
 * Controller handling Guest portal navigation and dedicated pages in Stage 2.
 * Strictly restricted to Role GUEST by Spring Security.
 * Provides completely isolated demonstration data in Demo Mode.
 */
@Controller
@RequestMapping("/guest")
public class GuestController {

    private final GuestService guestService;
    private final RoomService roomService;
    private final BookingService bookingService;
    private final HotelService hotelService;
    private final PlannedHotelService plannedHotelService;
    private final OfferService offerService;
    private final DemoDataService demoDataService;
    private final PreferenceMatcher preferenceMatcher;

    @Autowired
    public GuestController(GuestService guestService, RoomService roomService,
                           BookingService bookingService, HotelService hotelService,
                           PlannedHotelService plannedHotelService,
                           OfferService offerService,
                           DemoDataService demoDataService,
                           PreferenceMatcher preferenceMatcher) {
        this.guestService = guestService;
        this.roomService = roomService;
        this.bookingService = bookingService;
        this.hotelService = hotelService;
        this.plannedHotelService = plannedHotelService;
        this.offerService = offerService;
        this.demoDataService = demoDataService;
        this.preferenceMatcher = preferenceMatcher;
    }

    /**
     * Clean Navigation Dashboard showing the main option cards.
     */
    @GetMapping("/dashboard")
    public String dashboard(Model model, Principal principal, HttpSession session) {
        model.addAttribute("activeNav", "dashboard");

        if (demoDataService.isDemoMode(session)) {
            Guest demoGuest = demoDataService.getGuestProfile(session);
            model.addAttribute("guest", demoGuest);
            model.addAttribute("guestName", demoGuest.getFullName());
            model.addAttribute("plannedCount", demoDataService.getGuestPlannedCount(session));
            return "guest/dashboard";
        }

        String username = principal != null ? principal.getName() : "Guest";
        Optional<Guest> guestOpt = guestService.getGuestByUsername(username);

        Guest guest = guestOpt.orElse(new Guest(username, "", username, ""));
        model.addAttribute("guest", guest);
        model.addAttribute("guestName", guest.getFullName() != null && !guest.getFullName().isBlank() ? guest.getFullName() : username);
        model.addAttribute("plannedCount", plannedHotelService.getPlannedCountForGuest(username));

        return "guest/dashboard";
    }

    /**
     * Find Hotels navigation entry
     */
    @GetMapping("/find-hotels")
    public String findHotels() {
        return "redirect:/hotels";
    }

    /**
     * Dedicated My Bookings Page (Shows active/upcoming confirmed stays)
     */
    @GetMapping("/my-bookings")
    public String myBookings(@RequestParam(value = "booked", required = false) String booked,
                             Model model,
                             Principal principal,
                             HttpSession session) {
        model.addAttribute("activeNav", "my-bookings");

        if (demoDataService.isDemoMode(session)) {
            model.addAttribute("bookings", demoDataService.getGuestActiveBookings(session));
            model.addAttribute("serverNowMillis", System.currentTimeMillis());
            if (booked != null) {
                model.addAttribute("successMessage", "Your luxury room reservation was confirmed successfully! (Demo Mode)");
            }
            return "guest/my-bookings";
        }

        String username = principal != null ? principal.getName() : null;
        if (username == null) {
            return "redirect:/login?role=GUEST";
        }
        Optional<Guest> guestOpt = guestService.getGuestByUsername(username);

        List<Booking> bookings = Collections.emptyList();
        if (guestOpt.isPresent() && guestOpt.get().getId() != null) {
            bookings = bookingService.getActiveBookingsByGuest(guestOpt.get().getId());
        }

        model.addAttribute("bookings", bookings);
        model.addAttribute("serverNowMillis", System.currentTimeMillis());
        if (booked != null) {
            model.addAttribute("successMessage", "Your luxury room reservation was confirmed successfully!");
        }

        return "guest/my-bookings";
    }

    /**
     * Dedicated Booking History Page (Shows completed/past stays)
     */
    @GetMapping("/booking-history")
    public String bookingHistory(Model model, Principal principal, HttpSession session) {
        model.addAttribute("activeNav", "history");

        if (demoDataService.isDemoMode(session)) {
            model.addAttribute("bookings", demoDataService.getGuestPastBookings(session));
            return "guest/booking-history";
        }

        String username = principal != null ? principal.getName() : null;
        if (username == null) {
            return "redirect:/login?role=GUEST";
        }
        Optional<Guest> guestOpt = guestService.getGuestByUsername(username);

        List<Booking> bookings = Collections.emptyList();
        if (guestOpt.isPresent() && guestOpt.get().getId() != null) {
            bookings = bookingService.getPastBookingsByGuest(guestOpt.get().getId());
        }

        model.addAttribute("bookings", bookings);
        return "guest/booking-history";
    }

    /**
     * Dedicated Recommendations Page (Karnataka Luxury Picks)
     */
    @GetMapping("/recommendations")
    public String recommendations(Model model, Principal principal, HttpSession session) {
        model.addAttribute("activeNav", "recommendations");

        if (demoDataService.isDemoMode(session)) {
            model.addAttribute("hotels", hotelService.getAllKarnatakaHotels());
            model.addAttribute("plannedHotelIds", demoDataService.getGuestPlannedHotelIds(session));
            model.addAttribute("isPersonalized", false);
            return "guest/recommendations";
        }

        String username = principal != null ? principal.getName() : null;
        model.addAttribute("plannedHotelIds", plannedHotelService.getPlannedHotelIdsForGuest(username));

        List<Hotel> candidateHotels = hotelService.getAllHotels();
        boolean personalized = false;

        if (username != null) {
            Optional<Guest> guestOpt = guestService.getGuestByUsername(username);
            if (guestOpt.isPresent() && guestOpt.get().getPreference() != null) {
                GuestPreference pref = guestOpt.get().getPreference();
                PreferenceCriteria criteria = PreferenceCriteria.builder()
                        .state("Karnataka")
                        .roomType(pref.getPreferredCategory() != null ? pref.getPreferredCategory().name() : null)
                        .build();

                List<HotelMatchResult> rankedResults = preferenceMatcher.matchAndRankHotels(candidateHotels, criteria);
                List<Hotel> rankedHotels = rankedResults.stream()
                        .map(HotelMatchResult::getHotel)
                        .limit(20)
                        .toList();

                if (!rankedHotels.isEmpty()) {
                    model.addAttribute("hotels", rankedHotels);
                    personalized = true;
                }
            }
        }

        if (!personalized) {
            model.addAttribute("hotels", hotelService.getAllKarnatakaHotels());
        }
        model.addAttribute("isPersonalized", personalized);
        return "guest/recommendations";
    }

    /**
     * Dedicated Offers Page
     */
    @GetMapping("/offers")
    public String offers(Model model, HttpSession session) {
        model.addAttribute("activeNav", "offers");
        if (demoDataService.isDemoMode(session)) {
            model.addAttribute("offers", demoDataService.getDemoActiveOffers(session));
            return "guest/offers";
        }
        model.addAttribute("offers", offerService.getActiveOffers());
        return "guest/offers";
    }

    /**
     * Dedicated Hotel Services Page
     */
    @GetMapping("/services")
    public String services(Model model) {
        model.addAttribute("activeNav", "services");
        return "guest/services";
    }

    /**
     * Dedicated Explore by District Page
     */
    @GetMapping("/districts")
    public String exploreByDistrict(
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "district", required = false) String district,
            Model model) {
        model.addAttribute("activeNav", "districts");

        String activeState = state;
        if ((activeState == null || activeState.isBlank() || "All India".equalsIgnoreCase(activeState.trim()))
                && (district != null && !district.isBlank())) {
            String inferred = hotelService.getStateForDistrict(district.trim());
            if (inferred != null) {
                activeState = inferred;
            }
        }

        List<String> availableDistricts;
        List<Hotel> hotels;
        if (activeState != null && !activeState.isBlank() && !"All India".equalsIgnoreCase(activeState.trim())) {
            availableDistricts = hotelService.getDistrictsByState(activeState.trim());
            if (district != null && !district.isBlank() && !"All Districts".equalsIgnoreCase(district.trim())) {
                hotels = hotelService.getHotelsByDistrict(activeState.trim(), district.trim());
            } else {
                hotels = hotelService.getHotelsByState(activeState.trim());
            }
        } else {
            availableDistricts = hotelService.getAllDistricts();
            hotels = hotelService.getAllHotels();
        }

        model.addAttribute("selectedState", (activeState != null && !activeState.isBlank()) ? activeState : "All India");
        model.addAttribute("selectedDistrict", district != null ? district : "");
        model.addAttribute("districts", availableDistricts);
        model.addAttribute("hotels", hotels);
        return "guest/districts";
    }

    /**
     * Guest Profile View
     */
    @GetMapping("/profile")
    public String profile(Model model, Principal principal, HttpSession session) {
        model.addAttribute("activeNav", "profile");

        if (demoDataService.isDemoMode(session)) {
            model.addAttribute("guest", demoDataService.getGuestProfile(session));
            model.addAttribute("activeBookingsCount", demoDataService.getGuestActiveBookings(session).size());
            model.addAttribute("pastBookingsCount", demoDataService.getGuestPastBookings(session).size());
            return "guest/profile";
        }

        String username = principal != null ? principal.getName() : "Guest";
        Guest guest = guestService.getGuestByUsername(username)
                .orElse(new Guest(username, "", username, ""));
        model.addAttribute("guest", guest);

        if (guest.getId() != null) {
            model.addAttribute("activeBookingsCount", bookingService.getActiveBookingsByGuest(guest.getId()).size());
            model.addAttribute("pastBookingsCount", bookingService.getPastBookingsByGuest(guest.getId()).size());
        } else {
            model.addAttribute("activeBookingsCount", 0);
            model.addAttribute("pastBookingsCount", 0);
        }

        return "guest/profile";
    }

    /**
     * Legacy Search Rooms compatibility
     */
    @GetMapping("/search-rooms")
    public String searchRooms() {
        return "redirect:/hotels";
    }

    /**
     * Dedicated My Planned List Page.
     */
    @GetMapping("/planned-list")
    public String plannedList(Model model, Principal principal, HttpSession session) {
        model.addAttribute("activeNav", "planned-list");

        if (demoDataService.isDemoMode(session)) {
            List<PlannedHotel> plannedHotels = demoDataService.getGuestPlannedHotels(session);
            model.addAttribute("plannedHotels", plannedHotels);

            Map<Long, Booking> hotelBookingMap = new HashMap<>();
            Map<Long, String> hotelStatusMap = new HashMap<>();

            for (PlannedHotel p : plannedHotels) {
                if (p.getHotel() != null && p.getHotel().getId() != null) {
                    hotelStatusMap.put(p.getHotel().getId(), "Planned");
                }
            }

            model.addAttribute("hotelBookingMap", hotelBookingMap);
            model.addAttribute("hotelStatusMap", hotelStatusMap);
            model.addAttribute("serverNowMillis", System.currentTimeMillis());
            return "guest/planned-list";
        }

        String username = principal != null ? principal.getName() : "";
        List<PlannedHotel> plannedHotels = plannedHotelService.getPlannedHotelsForGuest(username);
        model.addAttribute("plannedHotels", plannedHotels);

        Map<Long, Booking> hotelBookingMap = new HashMap<>();
        Map<Long, String> hotelStatusMap = new HashMap<>();
        if (!username.isBlank()) {
            Optional<Guest> guestOpt = guestService.getGuestByUsername(username);
            if (guestOpt.isPresent() && guestOpt.get().getId() != null) {
                Long guestId = guestOpt.get().getId();
                List<Booking> activeBookings = bookingService.getActiveBookingsByGuest(guestId);
                for (Booking b : activeBookings) {
                    if (b.getHotel() != null && b.getHotel().getId() != null) {
                        hotelBookingMap.putIfAbsent(b.getHotel().getId(), b);
                        if (b.isStayCompleted()) {
                            hotelStatusMap.put(b.getHotel().getId(), "Completed");
                        } else {
                            hotelStatusMap.put(b.getHotel().getId(), "Booked");
                        }
                    }
                }

                List<Booking> pastBookings = bookingService.getPastBookingsByGuest(guestId);
                for (Booking b : pastBookings) {
                    if (b.getHotel() != null && b.getHotel().getId() != null) {
                        hotelStatusMap.putIfAbsent(b.getHotel().getId(), "Completed");
                    }
                }
            }
        }

        // For planned hotels without a booking, default status is "Planned"
        for (PlannedHotel p : plannedHotels) {
            if (p.getHotel() != null && p.getHotel().getId() != null) {
                hotelStatusMap.putIfAbsent(p.getHotel().getId(), "Planned");
            }
        }

        model.addAttribute("hotelBookingMap", hotelBookingMap);
        model.addAttribute("hotelStatusMap", hotelStatusMap);
        model.addAttribute("serverNowMillis", System.currentTimeMillis());

        return "guest/planned-list";
    }

    /**
     * Adds a hotel to the Guest's Planned List, optionally with planned dates.
     */
    @GetMapping("/planned-list/add")
    public String addPlannedHotel(@RequestParam Long hotelId,
                                  @RequestParam(value = "checkIn", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate checkIn,
                                  @RequestParam(value = "checkOut", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate checkOut,
                                  @RequestParam(value = "returnUrl", required = false) String returnUrl,
                                  RedirectAttributes redirectAttributes,
                                  Principal principal,
                                  HttpSession session) {
        if (demoDataService.isDemoMode(session)) {
            demoDataService.addGuestPlannedHotel(session, hotelId, checkIn, checkOut);
            redirectAttributes.addFlashAttribute("successMessage", "Added to your Planned List (Demo Mode)");
            redirectAttributes.addFlashAttribute("plannedMessage", "Added to your Planned List");
        } else if (principal != null) {
            plannedHotelService.addHotelToPlannedList(principal.getName(), hotelId, checkIn, checkOut);
            redirectAttributes.addFlashAttribute("successMessage", "Added to your Planned List");
            redirectAttributes.addFlashAttribute("plannedMessage", "Added to your Planned List");
        }
        if (returnUrl != null && !returnUrl.isBlank() && isSafeRedirect(returnUrl)) {
            return "redirect:" + returnUrl;
        }
        return "redirect:/guest/planned-list";
    }

    /**
     * Updates planned dates for a hotel in the Guest's Planned List.
     */
    @PostMapping({"/planned-list/update-dates", "/planned-list/dates"})
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updatePlannedDates(
            @RequestParam Long hotelId,
            @RequestParam(value = "checkIn", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate checkIn,
            @RequestParam(value = "checkOut", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate checkOut,
            Principal principal,
            HttpSession session) {

        if (demoDataService.isDemoMode(session)) {
            boolean updated = demoDataService.updateGuestPlannedDates(session, hotelId, checkIn, checkOut);
            Map<String, Object> response = new HashMap<>();
            response.put("success", updated);
            response.put("hotelId", hotelId);
            response.put("formattedStay", formatStayDates(checkIn, checkOut));
            response.put("checkIn", checkIn != null ? checkIn.toString() : "");
            response.put("checkOut", checkOut != null ? checkOut.toString() : "");
            response.put("message", "Planned stay dates updated successfully (Demo Mode)");
            return ResponseEntity.ok(response);
        }

        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "error", "Authentication required"));
        }

        boolean updated = plannedHotelService.updatePlannedDates(principal.getName(), hotelId, checkIn, checkOut);
        Map<String, Object> response = new HashMap<>();
        response.put("success", updated);
        response.put("hotelId", hotelId);
        response.put("formattedStay", formatStayDates(checkIn, checkOut));
        response.put("checkIn", checkIn != null ? checkIn.toString() : "");
        response.put("checkOut", checkOut != null ? checkOut.toString() : "");
        response.put("message", "Planned stay dates updated successfully");

        return ResponseEntity.ok(response);
    }

    private String formatStayDates(LocalDate checkIn, LocalDate checkOut) {
        if (checkIn == null && checkOut == null) {
            return "Dates not planned";
        }
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
        if (checkIn != null && checkOut != null) {
            return checkIn.format(formatter) + " → " + checkOut.format(formatter);
        } else if (checkIn != null) {
            return "From " + checkIn.format(formatter);
        } else {
            return "Until " + checkOut.format(formatter);
        }
    }

    /**
     * Form submission endpoint for updating planned dates with redirect.
     */
    @PostMapping("/planned-list/save-dates")
    public String savePlannedDatesForm(
            @RequestParam Long hotelId,
            @RequestParam(value = "checkIn", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate checkIn,
            @RequestParam(value = "checkOut", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate checkOut,
            @RequestParam(value = "returnUrl", required = false) String returnUrl,
            RedirectAttributes redirectAttributes,
            Principal principal,
            HttpSession session) {
        if (demoDataService.isDemoMode(session)) {
            demoDataService.updateGuestPlannedDates(session, hotelId, checkIn, checkOut);
            redirectAttributes.addFlashAttribute("successMessage", "Planned stay dates updated successfully (Demo Mode)");
        } else if (principal != null) {
            plannedHotelService.updatePlannedDates(principal.getName(), hotelId, checkIn, checkOut);
            redirectAttributes.addFlashAttribute("successMessage", "Planned stay dates updated successfully");
        }
        if (returnUrl != null && !returnUrl.isBlank() && isSafeRedirect(returnUrl)) {
            return "redirect:" + returnUrl;
        }
        return "redirect:/guest/planned-list";
    }

    /**
     * Removes a hotel from the Guest's Planned List via standard form or link.
     */
    @RequestMapping(value = "/planned-list/remove", method = {RequestMethod.GET, RequestMethod.POST})
    public String removePlannedHotel(@RequestParam Long hotelId,
                                     @RequestParam(value = "returnUrl", required = false) String returnUrl,
                                     RedirectAttributes redirectAttributes,
                                     Principal principal,
                                     HttpSession session) {
        if (demoDataService.isDemoMode(session)) {
            demoDataService.removeGuestPlannedHotel(session, hotelId);
            redirectAttributes.addFlashAttribute("successMessage", "Removed from your Planned List (Demo Mode)");
            redirectAttributes.addFlashAttribute("plannedMessage", "Removed from your Planned List");
        } else if (principal != null) {
            plannedHotelService.removeHotelFromPlannedList(principal.getName(), hotelId);
            redirectAttributes.addFlashAttribute("successMessage", "Removed from your Planned List");
            redirectAttributes.addFlashAttribute("plannedMessage", "Removed from your Planned List");
        }
        if (returnUrl != null && !returnUrl.isBlank() && isSafeRedirect(returnUrl)) {
            return "redirect:" + returnUrl;
        }
        return "redirect:/guest/planned-list";
    }

    /**
     * AJAX/REST endpoint for instant seamless toggle on hotel cards and details.
     */
    @PostMapping("/planned-list/toggle")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> togglePlannedHotelAjax(@RequestParam Long hotelId,
                                                                      Principal principal,
                                                                      HttpSession session) {
        if (demoDataService.isDemoMode(session)) {
            boolean isPlanned = demoDataService.toggleGuestPlannedHotel(session, hotelId);
            String message = isPlanned ? "Added to your Planned List" : "Removed from your Planned List";
            long totalCount = demoDataService.getGuestPlannedCount(session);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("planned", isPlanned);
            response.put("message", message);
            response.put("hotelId", hotelId);
            response.put("count", totalCount);
            return ResponseEntity.ok(response);
        }

        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "error", "Authentication required",
                                 "redirectUrl", "/login?role=GUEST&redirect=/guest/planned-list/add?hotelId=" + hotelId));
        }

        boolean isPlanned = plannedHotelService.togglePlannedHotel(principal.getName(), hotelId);
        String message = isPlanned ? "Added to your Planned List" : "Removed from your Planned List";
        long totalCount = plannedHotelService.getPlannedCountForGuest(principal.getName());

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("planned", isPlanned);
        response.put("message", message);
        response.put("hotelId", hotelId);
        response.put("count", totalCount);

        return ResponseEntity.ok(response);
    }

    private boolean isSafeRedirect(String url) {
        if (url == null || url.isBlank()) return false;
        if (!url.startsWith("/") || url.startsWith("//") || url.startsWith("/\\") || url.contains("\\")) return false;
        if (url.startsWith("/admin") || url.startsWith("/staff") || url.startsWith("/guest/profile")) return false;
        return true;
    }
}
