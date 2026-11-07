package uz.installment.security;

/**
 * Bo'lim darajasidagi ruxsatlar. Har bir foydalanuvchi rolining standart ruxsatlarini oladi;
 * administrator rol ruxsatlarini yoki alohida xodimning ruxsatlarini o'zgartirishi mumkin.
 * {@link #section} — interfeysda guruhlash uchun.
 */
public enum Permission {
    DASHBOARD_VIEW("dashboard"),

    CLIENTS_VIEW("clients"),
    CLIENTS_EDIT("clients"),

    CONTRACTS_VIEW("contracts"),
    CONTRACTS_CREATE("contracts"),
    /** Risk toifasi C (REVIEW) bo'lgan arizani tasdiqlash */
    CONTRACTS_APPROVE("contracts"),
    CONTRACTS_CANCEL("contracts"),

    PAYMENTS_VIEW("payments"),
    PAYMENTS_ACCEPT("payments"),

    PRODUCTS_VIEW("products"),
    PRODUCTS_EDIT("products"),

    COLLECTION_VIEW("collection"),
    /** Qo'ng'iroq, SMS, tashrif, talabnoma */
    COLLECTION_WORK("collection"),
    /** Mas'ul xodim biriktirish */
    COLLECTION_ASSIGN("collection"),
    /** Sud va MIB ishlari, da'vo arizasi */
    COLLECTION_LEGAL("collection"),

    NOTIFICATIONS_VIEW("notifications"),
    NOTIFICATIONS_EDIT("notifications"),

    /** Barcha xodimlar KPI'si (o'zinikini hamma ko'radi) */
    KPI_VIEW_ALL("analytics"),
    KPI_MANAGE("analytics"),
    FORECAST_VIEW("analytics"),
    /** Chuqur portfel tahlili: kategoriyalar, muddatlar, aging, reytinglar, foyda */
    ANALYTICS_VIEW("analytics"),

    BRANCHES_VIEW("branches"),
    BRANCHES_MANAGE("branches"),
    /** Barcha filiallar ma'lumotini ko'rish (aks holda faqat o'z filiali) */
    ALL_BRANCHES("branches"),

    USERS_MANAGE("system"),
    SETTINGS_EDIT("system"),
    DOCUMENTS_EDIT("system");

    private final String section;

    Permission(String section) {
        this.section = section;
    }

    public String section() {
        return section;
    }
}
