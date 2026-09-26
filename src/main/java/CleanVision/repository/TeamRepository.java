package CleanVision.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import CleanVision.domain.Team;

public interface TeamRepository extends JpaRepository<Team, Long> {

    List<Team> findByActiveTrueOrderByTeamNameAsc();

    List<Team> findByWardAndActiveTrue(String ward);
}
