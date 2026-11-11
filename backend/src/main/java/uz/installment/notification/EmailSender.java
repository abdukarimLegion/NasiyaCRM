package uz.installment.notification;

/**
 * Elektron pochta yuborish. Hozircha {@link LogEmailSender} (faqat log); haqiqiy SMTP/servis uchun shu
 * interfeysni amalga oshirib, bean'ni {@code @Primary} qiling.
 */
public interface EmailSender {

    MessageSender.Result send(String to, String subject, String text);

    /** true — xat haqiqatda yuborilmaydi (namoyish). */
    boolean isMock();
}
