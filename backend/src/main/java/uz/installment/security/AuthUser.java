package uz.installment.security;

/** JWT'dan olingan joriy foydalanuvchi (har so'rovda bazaga murojaat qilinmaydi). */
public record AuthUser(Long id, String username, Role role) {
}
