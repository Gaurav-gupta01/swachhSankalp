package CleanVision.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import CleanVision.domain.Team;
import CleanVision.domain.TeamType;

public interface TeamRepository extends JpaRepository<Team, Long> {

    List<Team> findByActiveTrueOrderByTeamNameAsc();

    boolean existsByTeamName(String teamName);

    List<Team> findByActiveTrueAndTeamTypeOrderByTeamNameAsc(TeamType teamType);

    List<Team> findByWardAndActiveTrue(String ward);

    List<Team> findByWardAndActiveTrueAndTeamType(String ward, TeamType teamType);
}
