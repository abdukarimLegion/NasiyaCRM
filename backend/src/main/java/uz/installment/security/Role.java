package uz.installment.security;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import static uz.installment.security.Permission.*;

public enum Role {
    ADMIN, CREDIT_OFFICER, COLLECTOR, CASHIER;

    /** Administrator har doim barcha ruxsatlarga ega (o'zini bloklab qo'ymasligi uchun o'zgartirilmaydi). */
    public boolean isAdmin() {
        return this == ADMIN;
    }

    /** Rol uchun standart ruxsatlar (role_permissions jadvalida sozlanmagan bo'lsa). */
    public Set<Permission> defaultPermissions() {
        return Collections.unmodifiableSet(switch (this) {
            case ADMIN -> EnumSet.allOf(Permission.class);
            case CREDIT_OFFICER -> EnumSet.of(DASHBOARD_VIEW, CLIENTS_VIEW, CLIENTS_EDIT, CONTRACTS_VIEW,
                    CONTRACTS_CREATE, PAYMENTS_VIEW, PAYMENTS_ACCEPT, PRODUCTS_VIEW, COLLECTION_VIEW,
                    COLLECTION_WORK, NOTIFICATIONS_VIEW, BRANCHES_VIEW);
            case COLLECTOR -> EnumSet.of(DASHBOARD_VIEW, CLIENTS_VIEW, CONTRACTS_VIEW, PAYMENTS_VIEW, PRODUCTS_VIEW,
                    COLLECTION_VIEW, COLLECTION_WORK, COLLECTION_LEGAL, NOTIFICATIONS_VIEW, BRANCHES_VIEW);
            case CASHIER -> EnumSet.of(DASHBOARD_VIEW, CLIENTS_VIEW, CONTRACTS_VIEW, PAYMENTS_VIEW, PAYMENTS_ACCEPT,
                    PRODUCTS_VIEW, NOTIFICATIONS_VIEW, BRANCHES_VIEW);
        });
    }
}
