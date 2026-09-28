package CleanVision.config;

import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import CleanVision.domain.DisposalFacility;
import CleanVision.domain.DisposalFacility.FacilityType;
import CleanVision.repository.DisposalFacilityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Seeds authorised disposal facilities around the demo city so smart disposal
 * routing has something to recommend on a fresh database.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FacilitySeeder implements ApplicationRunner {

    private final DisposalFacilityRepository facilities;

    @Override
    public void run(ApplicationArguments args) {
        if (facilities.count() > 0) {
            return;
        }

        seed("Margao Material Recovery Facility", FacilityType.MATERIAL_RECOVERY,
                15.2990, 73.9560, "Industrial Estate, Margao", "Ward 3",
                List.of("PLASTIC", "PAPER", "GLASS", "METAL", "MIXED"), "40 TPD",
                true, "020-4000111");

        seed("Sonsoddo Composting & Biogas Plant", FacilityType.COMPOSTING,
                15.2730, 73.9820, "Sonsoddo, Margao", "Ward 5",
                List.of("ORGANIC", "MIXED"), "25 TPD", true, "020-4000112");

        seed("Nauxi Recycling Centre", FacilityType.RECYCLING,
                15.3180, 73.9750, "Nauxi Village Road", "Ward 1",
                List.of("GLASS", "METAL", "PAPER", "PLASTIC"), "15 TPD", true, "020-4000113");

        seed("Zuarinagar Authorised E-Waste Centre", FacilityType.E_WASTE,
                15.2650, 73.9350, "Zuarinagar", "Ward 2",
                List.of("E_WASTE", "METAL"), "5 TPD", true, "020-4000114");

        seed("Cuncolim C&D Processing Plant", FacilityType.CND,
                15.2100, 73.9900, "Cuncolim Industrial Estate", "Ward 6",
                List.of("CONSTRUCTION", "MIXED"), "60 TPD", true, "020-4000115");

        seed("Margao Collection & Transfer Station", FacilityType.TRANSFER_STATION,
                15.2870, 73.9480, "Bus Stand Back Road, Margao", "Ward 4",
                List.of("MIXED", "PLASTIC", "PAPER", "GLASS", "METAL", "ORGANIC"),
                "80 TPD", true, "020-4000116");

        seed("Betalbatim Engineered Landfill", FacilityType.LANDFILL,
                15.2400, 73.9200, "Betalbatim, Margao", "Ward 7",
                List.of("MIXED", "CONSTRUCTION", "ORGANIC"), "120 TPD", true, "020-4000117");

        seed("Vasco Sorting Yard", FacilityType.TRANSFER_STATION,
                15.4000, 73.8100, "Vasco da Gama", "Ward 9",
                List.of("MIXED", "PLASTIC", "PAPER"), "30 TPD", false, "020-4000118");

        log.info("Seeded {} disposal facilities", facilities.count());
    }

    private void seed(String name, FacilityType type, double latitude, double longitude,
            String address, String ward, List<String> accepted, String capacity,
            boolean operational, String contact) {
        DisposalFacility facility = new DisposalFacility();
        facility.setName(name);
        facility.setFacilityType(type);
        facility.setLatitude(latitude);
        facility.setLongitude(longitude);
        facility.setAddress(address);
        facility.setWard(ward);
        facility.setAcceptedWasteTypes(String.join(",", accepted));
        facility.setCapacity(capacity);
        facility.setOperational(operational);
        facility.setContact(contact);
        facilities.save(facility);
    }
}
