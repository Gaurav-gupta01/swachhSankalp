package CleanVision.web;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import CleanVision.domain.DisposalFacility;
import CleanVision.domain.Report;
import CleanVision.domain.ReportStatus;
import CleanVision.domain.Team;
import CleanVision.domain.User;
import CleanVision.domain.WasteCategory;
import CleanVision.dto.Forms;
import CleanVision.repository.DisposalFacilityRepository;
import CleanVision.security.AuthInterceptor;
import CleanVision.service.AppException;
import CleanVision.service.FacilityService;
import CleanVision.service.ReportService;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/team")
@RequiredArgsConstructor
public class TeamController {

    private final ReportService reportService;
    private final FacilityService facilityService;
    private final DisposalFacilityRepository facilities;

    @GetMapping
    public String dashboard(Model model,
            @RequestAttribute(name = AuthInterceptor.CURRENT_USER, required = false) User user) {
        Team team = teamOf(user);
        List<Report> reports = reportService.byTeam(team);

        model.addAttribute("team", team);
        model.addAttribute("reports", reports);
        model.addAttribute("animalTeam",
                team.getTeamType() == CleanVision.domain.TeamType.ANIMAL);
        model.addAttribute("openCount", reports.stream()
                .filter(r -> r.getStatus().ordinal() < ReportStatus.RESOLVED.ordinal()).count());
        model.addAttribute("inProgressCount", reports.stream()
                .filter(r -> r.getStatus() == ReportStatus.IN_PROGRESS).count());
        model.addAttribute("resolvedCount", reports.stream()
                .filter(r -> r.getStatus() == ReportStatus.RESOLVED || r.getStatus() == ReportStatus.CLOSED)
                .count());
        return "team/dashboard";
    }

    @GetMapping("/reports/{publicId}")
    public String reportDetail(@PathVariable String publicId,
            @RequestAttribute(name = AuthInterceptor.CURRENT_USER, required = false) User user,
            Model model) {
        Team team = teamOf(user);
        Report report = reportService.byPublicId(publicId);
        requireAssignedTo(team, report);

        model.addAttribute("report", report);
        model.addAttribute("team", team);
        model.addAttribute("animalReport", report.getWasteType() == WasteCategory.ANIMAL);
        model.addAttribute("timeline", reportService.timeline(report));
        model.addAttribute("assignment", reportService.activeAssignment(report));
        model.addAttribute("mapPoints", List.of(mapPoint(report)));
        model.addAttribute("directionsUrl", directionsUrl(report));
        return "team/report-detail";
    }

    @PostMapping("/reports/{publicId}/start")
    public String startWork(@PathVariable String publicId,
            @RequestAttribute(name = AuthInterceptor.CURRENT_USER, required = false) User user,
            RedirectAttributes redirect) {
        Team team = teamOf(user);
        Report report = reportService.byPublicId(publicId);
        requireAssignedTo(team, report);

        if (report.getStatus().ordinal() >= ReportStatus.RESOLVED.ordinal()) {
            redirect.addFlashAttribute("errorMessage", publicId + " is already "
                    + report.getStatus().getLabel().toLowerCase() + ".");
            return "redirect:/team/reports/" + publicId;
        }
        boolean animal = report.getWasteType() == WasteCategory.ANIMAL;
        reportService.changeStatus(report, ReportStatus.IN_PROGRESS, user.getName(),
                (animal ? "Rescue started by " : "Cleanup started by ") + team.getTeamName());
        redirect.addFlashAttribute("flashMessage", animal
                ? publicId + " marked as rescue in progress"
                : publicId + " marked as In Progress");
        return "redirect:/team/reports/" + publicId;
    }

    /**
     * Rescue teams finish an animal report in one step: the after-rescue photo is
     * uploaded and the report is closed, so the citizen sees it as closed.
     */
    @PostMapping("/reports/{publicId}/close")
    public String closeRescue(@PathVariable String publicId,
            @RequestParam("file") MultipartFile file,
            @RequestAttribute(name = AuthInterceptor.CURRENT_USER, required = false) User user,
            RedirectAttributes redirect) {
        Team team = teamOf(user);
        Report report = reportService.byPublicId(publicId);
        requireAssignedTo(team, report);

        try {
            reportService.closeAfterRescue(report, file, user.getName());
            redirect.addFlashAttribute("flashMessage",
                    "Report " + publicId + " closed successfully");
        } catch (AppException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/team/reports/" + publicId;
    }

    @PostMapping("/reports/{publicId}/after")
    public String uploadAfter(@PathVariable String publicId,
            @RequestParam("file") MultipartFile file,
            @RequestAttribute(name = AuthInterceptor.CURRENT_USER, required = false) User user,
            RedirectAttributes redirect) {
        Team team = teamOf(user);
        Report report = reportService.byPublicId(publicId);
        requireAssignedTo(team, report);

        try {
            reportService.attachAfterImage(report, file, user.getName());
            redirect.addFlashAttribute("flashMessage", "After-cleanup photo saved for " + publicId);
        } catch (AppException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/team/reports/" + publicId;
    }

    @PostMapping("/reports/{publicId}/collect")
    public String collect(@PathVariable String publicId,
            @RequestParam(required = false) Double quantity,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestAttribute(name = AuthInterceptor.CURRENT_USER, required = false) User user,
            RedirectAttributes redirect) {
        Team team = teamOf(user);
        Report report = reportService.byPublicId(publicId);
        requireAssignedTo(team, report);

        try {
            reportService.markCollected(report, quantity, latitude, longitude, user.getName());
        } catch (AppException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/team/reports/" + publicId;
        }
        redirect.addFlashAttribute("flashMessage",
                publicId + " collected — showing suitable disposal facilities");
        return "redirect:/team/reports/" + publicId + "/disposal";
    }

    @GetMapping("/reports/{publicId}/disposal")
    public String disposal(@PathVariable String publicId,
            @RequestAttribute(name = AuthInterceptor.CURRENT_USER, required = false) User user,
            RedirectAttributes redirect,
            Model model) {
        Team team = teamOf(user);
        Report report = reportService.byPublicId(publicId);
        requireAssignedTo(team, report);

        if (report.getWasteType() == WasteCategory.ANIMAL) {
            redirect.addFlashAttribute("errorMessage",
                    "Animal rescue reports do not use waste disposal facilities.");
            return "redirect:/team/reports/" + publicId;
        }
        if (report.getStatus().ordinal() < ReportStatus.COLLECTED.ordinal()) {
            throw new AppException("Mark " + publicId + " as collected before looking for a facility");
        }
        if (report.getStatus().ordinal() >= ReportStatus.RESOLVED.ordinal()) {
            return "redirect:/team/reports/" + publicId;
        }

        double[] origin = originOf(report);
        double originLat = origin[0];
        double originLng = origin[1];

        FacilityService.RoutingResult routing = facilityService.recommend(
                report.getWasteType(), originLat, originLng);

        List<Forms.FacilitySuggestionView> suggestions = routing.suggestions().stream()
                .map(suggestion -> new Forms.FacilitySuggestionView(
                        suggestion.facility().getId(),
                        suggestion.facility().getName(),
                        suggestion.facility().getFacilityType() == null
                                ? ""
                                : suggestion.facility().getFacilityType().getLabel(),
                        suggestion.facility().getAddress(),
                        suggestion.facility().getWard(),
                        suggestion.facility().getAcceptedCategories().stream()
                                .map(c -> c.getLabel()).sorted().toList(),
                        suggestion.facility().isOperational(),
                        suggestion.distanceMeters(),
                        suggestion.durationSeconds(),
                        suggestion.distanceLabel(),
                        suggestion.durationLabel(),
                        suggestion.score(),
                        suggestion.navigateUrl(),
                        suggestion.facility().getLatitude(),
                        suggestion.facility().getLongitude()))
                .toList();

        model.addAttribute("report", report);
        model.addAttribute("team", team);
        model.addAttribute("suggestions", suggestions);
        model.addAttribute("routingAvailable", routing.routingAvailable());
        model.addAttribute("routingMessage", routing.message());
        model.addAttribute("originLat", originLat);
        model.addAttribute("originLng", originLng);
        model.addAttribute("originPoint", originPoint(originLat, originLng));
        model.addAttribute("facilityPoints", facilityPoints(suggestions));
        return "team/disposal";
    }

    @PostMapping("/reports/{publicId}/disposal")
    public String confirmDisposal(@PathVariable String publicId,
            @RequestParam Long facilityId,
            @RequestAttribute(name = AuthInterceptor.CURRENT_USER, required = false) User user,
            RedirectAttributes redirect) {
        Team team = teamOf(user);
        Report report = reportService.byPublicId(publicId);
        requireAssignedTo(team, report);

        DisposalFacility facility = facilities.findById(facilityId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Facility not found"));

        if (report.getWasteType() == WasteCategory.ANIMAL) {
            redirect.addFlashAttribute("errorMessage",
                    "Animal rescue reports do not use waste disposal facilities.");
            return "redirect:/team/reports/" + publicId;
        }

        double[] origin = originOf(report);
        double originLat = origin[0];
        double originLng = origin[1];

        try {
            double[] leg = facilityService.routeLeg(originLat, originLng, facility);
            reportService.confirmDisposal(report, facility,
                    leg == null ? null : leg[0], leg == null ? null : leg[1],
                    user.getName());
        } catch (AppException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/team/reports/" + publicId + "/disposal";
        }
        redirect.addFlashAttribute("flashMessage",
                publicId + " disposal confirmed at " + facility.getName() + " — report resolved");
        return "redirect:/team/reports/" + publicId;
    }

    private Map<String, Object> originPoint(double latitude, double longitude) {
        Map<String, Object> point = new LinkedHashMap<>();
        point.put("latitude", latitude);
        point.put("longitude", longitude);
        point.put("label", "Collected load");
        return point;
    }

    private List<Map<String, Object>> facilityPoints(List<Forms.FacilitySuggestionView> suggestions) {
        List<Map<String, Object>> points = new java.util.ArrayList<>();
        for (Forms.FacilitySuggestionView suggestion : suggestions) {
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("latitude", suggestion.latitude());
            point.put("longitude", suggestion.longitude());
            point.put("name", suggestion.name());
            point.put("facilityType", suggestion.facilityType());
            point.put("address", suggestion.address() == null ? "" : suggestion.address());
            point.put("distanceLabel", suggestion.distanceLabel());
            point.put("durationLabel", suggestion.durationLabel());
            point.put("operational", suggestion.operational());
            point.put("navigateUrl", suggestion.navigateUrl());
            points.add(point);
        }
        return points;
    }

    private double[] originOf(Report report) {
        Double lat = report.getTeamLatitude() != null
                ? report.getTeamLatitude() : report.getLatitude();
        Double lng = report.getTeamLongitude() != null
                ? report.getTeamLongitude() : report.getLongitude();
        if (lat == null || lng == null) {
            throw new AppException("No location available for " + report.getPublicId()
                    + " — cannot show nearby disposal facilities.");
        }
        return new double[] { lat, lng };
    }

    private Team teamOf(User user) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login required");
        }
        if (user.getTeam() == null) {
            throw new AppException("Your account is not linked to a cleanup team yet.");
        }
        return user.getTeam();
    }

    private void requireAssignedTo(Team team, Report report) {
        CleanVision.domain.Assignment assignment = reportService.activeAssignment(report);
        if (assignment == null || assignment.getTeam() == null
                || !assignment.getTeam().getId().equals(team.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    report.getPublicId() + " is not assigned to your team");
        }
    }

    private Map<String, Object> mapPoint(Report report) {
        Map<String, Object> point = new LinkedHashMap<>();
        point.put("publicId", report.getPublicId());
        point.put("wasteType", report.getWasteType().getLabel());
        point.put("severity", report.getSeverity().name());
        point.put("severityLabel", report.getSeverity().getLabel());
        point.put("status", report.getStatus().getLabel());
        point.put("statusCode", report.getStatus().name());
        point.put("ward", report.getWard() == null ? "" : report.getWard());
        point.put("latitude", report.getLatitude());
        point.put("longitude", report.getLongitude());
        return point;
    }

    private String directionsUrl(Report report) {
        String destination;
        if (report.getLatitude() != null && report.getLongitude() != null) {
            destination = report.getLatitude() + "," + report.getLongitude();
        } else if (report.getAddress() != null && !report.getAddress().isBlank()) {
            String address = report.getAddress()
                    + (report.getWard() == null || report.getWard().isBlank() ? "" : ", " + report.getWard());
            destination = URLEncoder.encode(address, StandardCharsets.UTF_8);
        } else {
            return null;
        }
        return "https://www.google.com/maps/dir/?api=1&destination=" + destination;
    }
}
