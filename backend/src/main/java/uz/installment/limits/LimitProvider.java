package uz.installment.limits;

/** Mijozning limiti ma'lumot sifatida tekshiriladigan tashqi nasiya platformalari. */
public enum LimitProvider {
    UZUM_NASIYA("Uzum Nasiya"),
    UZUM_BANK("Uzum Bank"),
    ANOR_BANK("Anor Bank"),
    OPEN_KART("Open kart"),
    CHAKANA("Chakana"),
    ALIF("Alif"),
    PAYME_NASIYA("Payme Nasiya"),
    INFIN("Infin muddatli to'lov kartasi"),
    PAYNET_NASIYA("Paynet Nasiya");

    private final String title;

    LimitProvider(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }
}
