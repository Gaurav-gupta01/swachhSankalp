package CleanVision.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import CleanVision.domain.Role;
import CleanVision.domain.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    List<User> findByPhone(String phone);

    /** One verified mobile number per citizen account. */
    boolean existsByRoleAndPhoneAndPhoneVerifiedTrue(Role role, String phone);

    long countByRole(Role role);

    java.util.Optional<User> findFirstByTeamIdAndRole(Long teamId, Role role);

    java.util.List<User> findByRole(Role role);
}
