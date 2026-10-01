package uz.installment.notification;

/** SMS yoki Telegram yuboruvchi adapter. Provayder almashtirilsa faqat implementatsiya o'zgaradi. */
public interface MessageSender {

    record Result(boolean success, String providerMessageId, String error) {
        public static Result ok(String id) {
            return new Result(true, id, null);
        }

        public static Result fail(String error) {
            return new Result(false, null, error);
        }
    }

    Result send(String recipient, String text);

    /** Sozlanmagan bo'lsa (kalit yo'q) — xabarlar SKIPPED deb yoziladi. */
    boolean isConfigured();
}
