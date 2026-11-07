package uz.installment.mib;

/** MIB qarzdorligini JShShIR bo'yicha tekshiruvchi adapter. Provayder almashtirilsa faqat implementatsiya o'zgaradi. */
public interface MibClient {

    /** Hech qachon exception tashlamaydi: xatolik {@link MibOutcome.Status#ERROR} bilan qaytariladi. */
    MibOutcome check(String pinfl);

    /** Provayder nomi (none / stub / http). */
    String name();

    /** Avtomatik tekshiruv ishlay oladimi (none — yo'q). */
    boolean isConfigured();
}
