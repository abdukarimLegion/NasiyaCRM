package uz.installment.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, Long> {

    Optional<NotificationTemplate> findByEventKey(String eventKey);

    List<NotificationTemplate> findAllByOrderByOffsetDaysAsc();
}
