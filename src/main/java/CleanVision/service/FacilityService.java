package CleanVision.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import CleanVision.domain.DisposalFacility;
import CleanVision.domain.WasteCategory;
import CleanVision.repository.DisposalFacilityRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Ranks authorised disposal facilities for a collected load.
 *
 * <p>Waste compatibility and facility availability are resolved from the
 * database, distances and travel times come from the live OSRM routing
 * service. No estimated numbers are invented when routing is unavailable.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FacilityService {

    private static final String OSRM_TABLE_URL =
            "https://router.project-osrm.org/table/v1/driving/";
    private static final double RADIUS_KM = 40;
    private static final int MAX_RESULTS = 6;

    private final DisposalFacilityRepository facilities;
    private final ObjectMapper mapper = new ObjectMapper();

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .build();

    /** A ranked facility option shown to the cleanup team. */
    public record FacilitySuggestion(
            DisposalFacility facility,
            boolean compatible,
            Double distanceMeters,
            Double durationSeconds,
            int score,
            String navigateUrl) {

        public String distanceLabel() {
            if (distanceMeters == null) {
                return "—";
            }
            return distanceMeters < 1000
                    ? Math.round(distanceMeters) + " m"
                    : String.format("%.1f km", distanceMeters / 1000);
        }

        public String durationLabel() {
            if (durationSeconds == null) {
                return "—";
            }
            long minutes = Math.max(1, Math.round(durationSeconds / 60));
            if (minutes < 60) {
                return minutes + " min";
            }
            return (minutes / 60) + " h " + (minutes % 60) + " min";
        }
    }

    /** Result of a recommendation request, including routing availability. */
    public record RoutingResult(
            List<FacilitySuggestion> suggestions,
            boolean routingAvailable,
            String message,
            double originLat,
            double originLng) {
    }

    @Transactional(readOnly = true)
    public RoutingResult recommend(WasteCategory wasteType, double originLat, double originLng) {
        WasteCategory target = wasteType == null || wasteType == WasteCategory.OTHER
                ? WasteCategory.MIXED
                : wasteType;

        List<DisposalFacility> compatible = facilities.findAllByOrderByOperationalDescNameAsc()
                .stream()
                .filter(facility -> facility.accepts(target))
                .filter(facility -> haversineKm(originLat, originLng,
                        facility.getLatitude(), facility.getLongitude()) <= RADIUS_KM)
                .toList();

        if (compatible.isEmpty()) {
            return new RoutingResult(List.of(), true,
                    "No authorised facility within " + (int) RADIUS_KM
                            + " km accepts " + target.getLabel() + ".",
                    originLat, originLng);
        }

        Map<Long, double[]> routes = osrmMatrix(originLat, originLng, compatible);
        boolean routingAvailable = routes != null;

        List<FacilitySuggestion> suggestions = new ArrayList<>();
        double longestSeconds = 0;
        if (routingAvailable) {
            for (DisposalFacility facility : compatible) {
                double[] leg = routes.get(facility.getId());
                if (leg != null && leg[1] > longestSeconds) {
                    longestSeconds = leg[1];
                }
            }
        }

        for (DisposalFacility facility : compatible) {
            double[] leg = routingAvailable ? routes.get(facility.getId()) : null;
            Double meters = leg == null ? null : leg[0];
            Double seconds = leg == null ? null : leg[1];

            int compatibility = facility.accepts(target) ? 40 : 0;
            int availability = facility.isOperational() ? 30 : 0;
            int speed = 0;
            if (seconds != null) {
                speed = (int) Math.round(30 * (1 - Math.min(seconds, longestSeconds)
                        / Math.max(longestSeconds, 1)));
            }
            suggestions.add(new FacilitySuggestion(
                    facility,
                    compatibility > 0,
                    meters,
                    seconds,
                    compatibility + availability + speed,
                    navigateUrl(facility)));
        }

        Comparator<FacilitySuggestion> ranking = Comparator
                .comparing((FacilitySuggestion s) -> !s.facility().isOperational())
                .thenComparing(s -> s.durationSeconds() == null
                        ? haversineKm(originLat, originLng,
                                s.facility().getLatitude(), s.facility().getLongitude())
                        : s.durationSeconds())
                .thenComparing(s -> -s.score());
        suggestions.sort(ranking);

        String message = routingAvailable
                ? "Sorted by waste compatibility, facility availability and live route time."
                : "Live routing is unavailable right now — facilities are ordered by proximity, "
                        + "distances and travel times will appear once the routing service responds.";

        return new RoutingResult(
                List.copyOf(suggestions.subList(0, Math.min(MAX_RESULTS, suggestions.size()))),
                routingAvailable, message, originLat, originLng);
    }

    public String navigateUrl(DisposalFacility facility) {
        return "https://www.google.com/maps/dir/?api=1&destination="
                + facility.getLatitude() + "," + facility.getLongitude();
    }

    /** Live road distance and travel time for a single origin → facility leg. */
    public double[] routeLeg(double originLat, double originLng, DisposalFacility facility) {
        String url = OSRM_TABLE_URL + originLng + ',' + originLat + ';'
                + facility.getLongitude() + ',' + facility.getLatitude()
                + "?sources=0&annotations=distance,duration";
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return null;
            }
            JsonNode root = mapper.readTree(response.body());
            if (!"Ok".equalsIgnoreCase(root.path("code").asText())) {
                return null;
            }
            JsonNode distance = root.path("distances").path(0).path(1);
            JsonNode duration = root.path("durations").path(0).path(1);
            if (distance == null || distance.isNull() || duration == null || duration.isNull()) {
                return null;
            }
            return new double[] { distance.asDouble(), duration.asDouble() };
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception ex) {
            log.warn("OSRM route failed: {}", ex.getMessage());
            return null;
        }
    }

    public double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double earthRadiusKm = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double value = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * earthRadiusKm * Math.asin(Math.sqrt(value));
    }

    /** facility id → [meters, seconds] straight from OSRM, or null when unreachable. */
    private Map<Long, double[]> osrmMatrix(double originLat, double originLng,
            List<DisposalFacility> targets) {
        StringBuilder coordinates = new StringBuilder()
                .append(originLng).append(',').append(originLat);
        for (DisposalFacility facility : targets) {
            coordinates.append(';')
                    .append(facility.getLongitude()).append(',')
                    .append(facility.getLatitude());
        }
        String url = OSRM_TABLE_URL + coordinates
                + "?sources=0&annotations=distance,duration";

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("OSRM returned {}", response.statusCode());
                return null;
            }
            JsonNode root = mapper.readTree(response.body());
            if (!"Ok".equalsIgnoreCase(root.path("code").asText())) {
                log.warn("OSRM response code {}", root.path("code").asText());
                return null;
            }
            JsonNode distances = root.path("distances").path(0);
            JsonNode durations = root.path("durations").path(0);
            if (!distances.isArray() || !durations.isArray()
                    || distances.size() < targets.size() + 1 || durations.size() < targets.size() + 1) {
                return null;
            }

            Map<Long, double[]> result = new LinkedHashMap<>();
            for (int i = 0; i < targets.size(); i++) {
                JsonNode distance = distances.get(i + 1);
                JsonNode duration = durations.get(i + 1);
                if (distance == null || distance.isNull() || duration == null || duration.isNull()) {
                    continue;
                }
                result.put(targets.get(i).getId(),
                        new double[] { distance.asDouble(), duration.asDouble() });
            }
            return result;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception ex) {
            log.warn("OSRM routing failed: {}", ex.getMessage());
            return null;
        }
    }
}
