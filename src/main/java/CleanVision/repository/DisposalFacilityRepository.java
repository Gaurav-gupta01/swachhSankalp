package CleanVision.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import CleanVision.domain.DisposalFacility;

public interface DisposalFacilityRepository extends JpaRepository<DisposalFacility, Long> {

    List<DisposalFacility> findAllByOrderByOperationalDescNameAsc();

    List<DisposalFacility> findByOperationalTrue();

    Optional<DisposalFacility> findByNameIgnoreCase(String name);
}
