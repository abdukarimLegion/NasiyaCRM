package uz.installment.security;

/** JWT'dan olingan joriy foydalanuvchi; rol va active holati har so'rovda bazadan tekshiriladi. */
public record AuthUser(Long id, String username, Role role) {
}
