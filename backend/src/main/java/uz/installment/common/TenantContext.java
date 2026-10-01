package uz.installment.common;

/**
 * Joriy tenant (kompaniya). Hozircha bitta mijoz serverida ishlaydi — doim 1.
 * SaaS bosqichida bu qiymat JWT / subdomen orqali request boshida o'rnatiladi
 * va Hibernate filtri / PostgreSQL RLS bilan ishlatiladi.
 */
public final class TenantContext {

    public static final long DEFAULT_TENANT = 1L;

    private TenantContext() {
    }

    public static long currentTenantId() {
        return DEFAULT_TENANT;
    }
}
