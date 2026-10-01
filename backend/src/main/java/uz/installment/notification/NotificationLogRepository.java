package uz.installment.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    boolean existsByEventKeyAndScheduleItemIdAndChannelAndStatus(String eventKey, Long scheduleItemId,
                                                                 NotificationChannel channel,
                                                                 NotificationLog.Status status);

    Page<NotificationLog> findAllByOrderByIdDesc(Pageable pageable);
}
