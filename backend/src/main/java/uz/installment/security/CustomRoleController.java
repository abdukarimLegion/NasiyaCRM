package uz.installment.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uz.installment.common.BusinessException;
import uz.installment.common.NotFoundException;
import uz.installment.common.TenantContext;

import java.util.List;
import java.util.Map;

/**
 * Shaxsiy rollar: administrator o'z nomi, rangi va ruxsatlar to'plami bilan rol yaratadi (masalan, "Katta kassir").
 * Rol xodimga biriktiriladi; to'plam o'zgarsa, shu roldagi barcha xodimlarda darhol o'zgaradi.
 */
@RestController
@RequestMapping("/api/permissions/custom-roles")
@PreAuthorize("hasAuthority('USERS_MANAGE')")
@RequiredArgsConstructor
public class CustomRoleController {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;
    private final AccessService access;

    public record CustomRole(Long id, String name, String description, String color, Role baseRole,
                             List<Permission> permissions, int users) {
    }

    public record CustomRoleRequest(@NotBlank @Size(max = 80) String name, @Size(max = 200) String description,
                                    @Pattern(regexp = "^#[0-9a-fA-F]{6}$") String color, @NotNull Role baseRole,
                                    @NotNull List<Permission> permissions) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<CustomRole> list() {
        return jdbc.query("""
                select r.id, r.name, r.description, r.color, r.base_role, r.permissions::text as perms,
                       (select count(*) from users u where u.custom_role_id = r.id) as cnt
                from custom_roles r where r.tenant_id = :t order by r.name
                """, Map.of("t", TenantContext.currentTenantId()), (rs, i) -> new CustomRole(rs.getLong("id"),
                rs.getString("name"), rs.getString("description"), rs.getString("color"),
                Role.valueOf(rs.getString("base_role")), AccessService.parse(names(rs.getString("perms"))).stream().sorted().toList(),
                rs.getInt("cnt")));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public CustomRole create(@Valid @RequestBody CustomRoleRequest r) {
        checkBase(r.baseRole());
        try {
            Long id = jdbc.queryForObject("""
                    insert into custom_roles (tenant_id, name, description, color, base_role, permissions)
                    values (:t, :n, :d, :c, :b, cast(:p as jsonb)) returning id
                    """, params(r).addValue("t", TenantContext.currentTenantId()), Long.class);
            return get(id);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new BusinessException("ROLE_NAME_TAKEN", "Bu nomli rol mavjud: " + r.name());
        }
    }

    @PutMapping("/{id}")
    @Transactional
    public CustomRole update(@PathVariable Long id, @Valid @RequestBody CustomRoleRequest r) {
        checkBase(r.baseRole());
        get(id);
        try {
            jdbc.update("""
                    update custom_roles set name = :n, description = :d, color = :c, base_role = :b,
                           permissions = cast(:p as jsonb), updated_at = now() where id = :id
                    """, params(r).addValue("id", id));
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new BusinessException("ROLE_NAME_TAKEN", "Bu nomli rol mavjud: " + r.name());
        }
        access.invalidateAll();
        return get(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void delete(@PathVariable Long id) {
        CustomRole r = get(id);
        if (r.users() > 0) {
            throw new BusinessException("ROLE_IN_USE", "Rol " + r.users() + " ta xodimga biriktirilgan — avval ularni boshqa rolga o'tkazing");
        }
        jdbc.update("delete from custom_roles where id = :id", Map.of("id", id));
    }

    private CustomRole get(Long id) {
        return list().stream().filter(r -> r.id().equals(id)).findFirst()
                .orElseThrow(() -> new NotFoundException("Rol", id));
    }

    private static void checkBase(Role base) {
        if (base.isAdmin()) {
            throw new BusinessException("ROLE_BASE", "Shaxsiy rolning asosiy roli administrator bo'la olmaydi");
        }
    }

    private MapSqlParameterSource params(CustomRoleRequest r) {
        try {
            return new MapSqlParameterSource().addValue("n", r.name().trim()).addValue("d", r.description())
                    .addValue("c", r.color() == null ? "#64748b" : r.color()).addValue("b", r.baseRole().name())
                    .addValue("p", json.writeValueAsString(r.permissions().stream().map(Enum::name).distinct().sorted().toList()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private List<String> names(String raw) {
        try {
            return json.readValue(raw, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }
}
