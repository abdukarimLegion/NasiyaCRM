package uz.installment.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import uz.installment.client.Client;
import uz.installment.contract.Contract;
import uz.installment.contract.ContractCreatedEvent;
import uz.installment.contract.ContractRepository;
import uz.installment.contract.ScheduleItem;
import uz.installment.contract.ScheduleItemRepository;
import uz.installment.settings.SettingsService;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationTemplateRepository templates;
    private final NotificationLogRepository logs;
    private final ScheduleItemRepository scheduleItems;
    private final ContractRepository contracts;
    private final SettingsService settings;
    private final SmsSender sms;
    private final TelegramSender telegram;
    private final Clock clock;

    public record ReminderStats(int sent, int failed, int skipped) {
    }

    /** Shartnoma commit bo'lgandan keyin — SMS xatosi shartnomani bekor qilmasligi kerak. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onContractCreated(ContractCreatedEvent event) {
        try {
            templates.findByEventKey("CONTRACT").filter(NotificationTemplate::isEnabled).ifPresent(t -> {
                Contract c = contracts.findWithSchedule(event.contractId()).orElseThrow();
                String lang = settings.current().getDefaultLang();
                String text = TemplateRenderer.render(pick(t, lang), c.getClient().getFullName(),
                        c.getMonthlyPayment(), c.getFirstDueDate(), c.getContractNo(), 0, lang);
                dispatch(t, c.getClient(), c, null, text);
            });
        } catch (RuntimeException e) {
            log.error("Shartnoma SMS'ini yuborishda xato (contract={})", event.contractId(), e);
        }
    }

    /**
     * Kunlik eslatmalar. offset_days = -3 bo'lsa: 3 kundan keyin to'lanadigan qismlar,
     * offset_days = 7 bo'lsa: 7 kun oldin to'lanishi kerak bo'lgan (hali yopilmagan) qismlar.
     */
    @Transactional
    public ReminderStats sendDailyReminders() {
        LocalDate today = LocalDate.now(clock);
        String lang = settings.current().getDefaultLang();
        int sent = 0, failed = 0, skipped = 0;
        for (NotificationTemplate t : templates.findAllByOrderByOffsetDaysAsc()) {
            if (!t.isEnabled() || !t.isScheduleEvent()) {
                continue;
            }
            LocalDate due = today.minusDays(t.getOffsetDays());
            for (ScheduleItem item : scheduleItems.findOpenDueOn(due)) {
                Contract c = item.getContract();
                long daysLate = Math.max(0, ChronoUnit.DAYS.between(item.getDueDate(), today));
                BigDecimal amount = t.getOffsetDays() > 0 ? overdueAmount(c, today) : item.remaining();
                String text = TemplateRenderer.render(pick(t, lang), c.getClient().getFullName(), amount,
                        item.getDueDate(), c.getContractNo(), daysLate, lang);
                for (NotificationLog.Status s : dispatch(t, c.getClient(), c, item, text)) {
                    switch (s) {
                        case SENT -> sent++;
                        case FAILED -> failed++;
                        case SKIPPED -> skipped++;
                    }
                }
            }
        }
        log.info("Eslatmalar: yuborildi={}, xato={}, o'tkazildi={}", sent, failed, skipped);
        return new ReminderStats(sent, failed, skipped);
    }

    private List<NotificationLog.Status> dispatch(NotificationTemplate t, Client client, Contract contract,
                                                  ScheduleItem item, String text) {
        List<NotificationLog.Status> out = new ArrayList<>();
        if (t.getChannel() == NotificationChannel.SMS || t.getChannel() == NotificationChannel.BOTH) {
            out.add(sendOne(t.getEventKey(), NotificationChannel.SMS, sms, client.getPhone(), client, contract, item, text));
        }
        if (t.getChannel() == NotificationChannel.TELEGRAM || t.getChannel() == NotificationChannel.BOTH) {
            String chat = client.getTelegramChatId() == null ? null : client.getTelegramChatId().toString();
            out.add(sendOne(t.getEventKey(), NotificationChannel.TELEGRAM, telegram, chat, client, contract, item, text));
        }
        return out;
    }

    private NotificationLog.Status sendOne(String eventKey, NotificationChannel channel, MessageSender sender,
                                           String recipient, Client client, Contract contract, ScheduleItem item,
                                           String text) {
        Long itemId = item == null ? null : item.getId();
        if (itemId != null && logs.existsByEventKeyAndScheduleItemIdAndChannelAndStatus(
                eventKey, itemId, channel, NotificationLog.Status.SENT)) {
            return NotificationLog.Status.SKIPPED; // bugun allaqachon yuborilgan (job qayta ishga tushgan)
        }
        NotificationLog entry = new NotificationLog();
        entry.setEventKey(eventKey);
        entry.setChannel(channel);
        entry.setClientId(client.getId());
        entry.setContractId(contract.getId());
        entry.setScheduleItemId(itemId);
        entry.setMessage(text);

        if (recipient == null || !sender.isConfigured()) {
            entry.setRecipient(recipient == null ? "-" : recipient);
            entry.setStatus(NotificationLog.Status.SKIPPED);
            entry.setError(recipient == null ? "Qabul qiluvchi yo'q" : "Kanal sozlanmagan");
        } else {
            entry.setRecipient(recipient);
            MessageSender.Result r = sender.send(recipient, text);
            entry.setStatus(r.success() ? NotificationLog.Status.SENT : NotificationLog.Status.FAILED);
            entry.setProviderMessageId(r.providerMessageId());
            entry.setError(r.error());
        }
        logs.save(entry);
        return entry.getStatus();
    }

    /** Kechikish xabarlarida bitta oy emas, jami muddati o'tgan qarz ko'rsatiladi. */
    private static BigDecimal overdueAmount(Contract c, LocalDate today) {
        return c.getSchedule().stream().filter(s -> s.isOverdue(today))
                .map(ScheduleItem::remaining).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String pick(NotificationTemplate t, String lang) {
        return "ru".equals(lang) ? t.getTextRu() : t.getTextUz();
    }
}
