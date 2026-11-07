package uz.installment.security;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByRole(Role role);

    boolean existsByPinflAndIdNot(String pinfl, Long id);

    List<User> findAllByOrderByFullNameAsc();
}
