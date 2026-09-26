package CleanVision.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import CleanVision.domain.Role;
import CleanVision.domain.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    long countByRole(Role role);

    java.util.Optional<User> findFirstByTeamIdAndRole(Long teamId, Role role);

    java.util.List<User> findByRole(Role role);
}
