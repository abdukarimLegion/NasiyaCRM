package uz.installment.security;

import java.util.Set;

/**
 * Joriy foydalanuvchi: JWT'dagi id + bazadan (keshdan) olingan rol, filial va ruxsatlar.
 * Ruxsat, filial yoki active holat o'zgarsa, keyingi so'rovdan boshlab amal qiladi
 * (token qayta berilishi shart emas).
 */
public record AuthUser(Long id, String username, Role role, Long branchId, Set<Permission> permissions) {

    public boolean has(Permission p) {
        return permissions.contains(p);
    }

    /**
     * Ma'lumot qaysi filial bo'yicha ko'rsatiladi: ALL_BRANCHES ruxsati bo'lsa — so'ralgan filial
     * (null = hammasi), aks holda faqat o'z filiali. Filiali yo'q xodim (bosh ofis) so'ralganini ko'radi.
     */
    public Long branchScope(Long requested) {
        if (has(Permission.ALL_BRANCHES) || branchId == null) {
            return requested;
        }
        return branchId;
    }
}
