package uz.installment.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import uz.installment.common.TenantContext;

import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Foydalanuvchining amaldagi ruxsatlari va filiali. Har so'rovda bazaga bormaslik uchun keshlanadi;
 * foydalanuvchi yoki rol ruxsatlari o'zgarganda kesh tozalanadi (bitta instansiya uchun yetarli).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccessService {

    /** Keshdagi yozuv: faol emas yoki o'chirilgan foydalanuvchi uchun so'rov rad etiladi. */
    public record Access(Long id, String username, Role role, boolean active, Long branchId,
                         Set<Permission> permissions) {
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Map<Long, Access> cache = new ConcurrentHashMap<>();
    private volatile Map<Role, Set<Permission>> rolePermissions;

    public Optional<AuthUser> load(long userId) {
        Access a = cache.computeIfAbsent(userId, this::fetch);
        if (a == null || !a.active()) {
            cache.remove(userId);
            return Optional.empty();
        }
        return Optional.of(new AuthUser(a.id(), a.username(), a.role(), a.branchId(), a.permissions()));
    }

    /** Rol uchun amaldagi ruxsatlar (bazada sozlangan bo'lsa — o'sha, aks holda standart). */
    public Set<Permission> roleEffective(Role role) {
        if (role.isAdmin()) {
            return role.defaultPermissions();
        }
        return rolePermissions().getOrDefault(role, role.defaultPermissions());
    }

    /** Foydalanuvchi uchun: shaxsiy ruxsatlar bo'lsa — o'sha, aks holda rolniki. */
    public Set<Permission> effective(Role role, List<String> custom) {
        return effective(role, custom, null);
    }

    /** Ustuvorlik: administrator → xodimning shaxsiy ruxsatlari → shaxsiy rol to'plami → tizim roli. */
    public Set<Permission> effective(Role role, List<String> custom, Long customRoleId) {
        if (role.isAdmin()) {
            return roleEffective(role);
        }
        if (custom != null) {
            return parse(custom);
        }
        if (customRoleId != null) {
            List<String> perms = jdbc.query("select permissions::text from custom_roles where id = :id",
                    Map.of("id", customRoleId), (rs, i) -> rs.getString(1)).stream().findFirst().map(this::fromJson).orElse(null);
            if (perms != null) {
                return parse(perms);
            }
        }
        return roleEffective(role);
    }

    public Map<Role, Set<Permission>> allRoles() {
        Map<Role, Set<Permission>> m = new EnumMap<>(Role.class);
        for (Role r : Role.values()) {
            m.put(r, roleEffective(r));
        }
        return m;
    }

    @Transactional
    public void saveRole(Role role, Collection<Permission> permissions) {
        if (role.isAdmin()) {
            throw new IllegalArgumentException("Administrator ruxsatlari o'zgartirilmaydi");
        }
        String value = toJson(permissions.stream().map(Enum::name).sorted().toList());
        jdbc.update("""
                insert into role_permissions (tenant_id, role, permissions, updated_at)
                values (:t, :role, cast(:p as jsonb), now())
                on conflict (tenant_id, role) do update set permissions = excluded.permissions, updated_at = now()
                """, Map.of("t", TenantContext.currentTenantId(), "role", role.name(), "p", value));
        invalidateAll();
    }

    @Transactional
    public void resetRole(Role role) {
        jdbc.update("delete from role_permissions where tenant_id = :t and role = :role",
                Map.of("t", TenantContext.currentTenantId(), "role", role.name()));
        invalidateAll();
    }

    /** Tranzaksiya ichida chaqirilsa — commit'dan keyin tozalanadi (eski qiymat qayta keshlanmasligi uchun). */
    public void invalidate(Long userId) {
        afterCommit(() -> cache.remove(userId));
    }

    public void invalidateAll() {
        afterCommit(() -> {
            cache.clear();
            rolePermissions = null;
        });
    }

    private static void afterCommit(Runnable r) {
        r.run();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    r.run();
                }
            });
        }
    }

    public static Set<Permission> parse(Collection<String> names) {
        Set<Permission> s = EnumSet.noneOf(Permission.class);
        for (String n : names) {
            try {
                s.add(Permission.valueOf(n));
            } catch (IllegalArgumentException e) {
                // keyingi versiyalarda olib tashlangan ruxsat — e'tiborsiz qoldiriladi
            }
        }
        return s;
    }

    private Access fetch(Long userId) {
        List<Access> rows = jdbc.query("""
                select id, username, role, active, branch_id, permissions::text as permissions, custom_role_id
                from users where id = :id
                """, Map.of("id", userId), (rs, i) -> {
            Role role = Role.valueOf(rs.getString("role"));
            String perms = rs.getString("permissions");
            List<String> custom = perms == null ? null : fromJson(perms);
            return new Access(rs.getLong("id"), rs.getString("username"), role, rs.getBoolean("active"),
                    (Long) rs.getObject("branch_id", Long.class),
                    effective(role, custom, rs.getObject("custom_role_id", Long.class)));
        });
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Map<Role, Set<Permission>> rolePermissions() {
        Map<Role, Set<Permission>> m = rolePermissions;
        if (m == null) {
            Map<Role, Set<Permission>> loaded = new EnumMap<>(Role.class);
            jdbc.query("select role, permissions::text as p from role_permissions where tenant_id = :t",
                    Map.of("t", TenantContext.currentTenantId()), rs -> {
                        loaded.put(Role.valueOf(rs.getString("role")), parse(fromJson(rs.getString("p"))));
                    });
            rolePermissions = m = loaded;
        }
        return m;
    }

    private List<String> fromJson(String s) {
        try {
            return json.readValue(s, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            log.warn("Ruxsatlar JSON'i o'qilmadi: {}", s);
            return List.of();
        }
    }

    private String toJson(List<String> list) {
        try {
            return json.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
