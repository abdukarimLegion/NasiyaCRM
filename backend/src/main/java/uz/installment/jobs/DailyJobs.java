package uz.installment.jobs;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uz.installment.contract.ContractRepository;
import uz.installment.contract.ScheduleItemRepository;
import uz.installment.notification.NotificationService;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Kunlik ishlar (Toshkent vaqti bilan):
 * 1) muddati o'tgan qismlar va shartnomalarni LATE qilish;
 * 2) SMS/Telegram eslatmalar.
 * Bitta server bo'lgani uchun ShedLock kerak emas; SaaS'da bir nechta instansiya bo'lsa qo'shiladi.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DailyJobs {

    private final ScheduleItemRepository scheduleItems;
    private final ContractRepository contracts;
    private final NotificationService notifications;
    private final Clock clock;

    @Scheduled(cron = "${app.jobs.daily-cron}", zone = "${app.timezone}")
    public void daily() {
        refreshOverdue();
        try {
            notifications.sendDailyReminders();
        } catch (RuntimeException e) {
            log.error("Eslatmalar job'i xato bilan tugadi", e);
        }
    }

    /** Server qayta ishga tushsa ham holatlar darhol to'g'ri bo'lsin. */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        refreshOverdue();
    }

    public void refreshOverdue() {
        LocalDate today = LocalDate.now(clock);
        int items = scheduleItems.markLateItems(today);
        int ctr = contracts.markLateContracts(today);
        log.info("Kechikishlar yangilandi: {} ta qism, {} ta shartnoma", items, ctr);
    }
}
