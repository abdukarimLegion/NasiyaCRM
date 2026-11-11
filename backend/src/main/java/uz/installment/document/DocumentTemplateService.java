package uz.installment.document;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.installment.common.NotFoundException;
import uz.installment.common.TenantContext;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Hujjat shablonlari (shartnoma, talabnoma, da'vo arizasi) — Sozlamalarda tahrirlanadi. */
@Service
@RequiredArgsConstructor
public class DocumentTemplateService {

    public enum Code { CONTRACT, DEMAND, CLAIM, EMPLOYER }

    public record Template(Code code, String lang, String title, String body, Instant updatedAt) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public List<Template> list() {
        return jdbc.query("""
                select code, lang, title, body, updated_at from document_templates
                where tenant_id = :t order by code, lang
                """, Map.of("t", TenantContext.currentTenantId()), (rs, i) -> new Template(
                Code.valueOf(rs.getString("code")), rs.getString("lang"), rs.getString("title"),
                rs.getString("body"), rs.getTimestamp("updated_at").toInstant()));
    }

    public Template get(Code code, String lang) {
        return list().stream().filter(t -> t.code() == code && t.lang().equals(lang)).findFirst()
                .orElseThrow(() -> new NotFoundException("Shablon", code + "/" + lang));
    }

    @Transactional
    public Template save(Code code, String lang, String title, String body) {
        var p = new MapSqlParameterSource().addValue("t", TenantContext.currentTenantId())
                .addValue("c", code.name()).addValue("l", lang).addValue("title", title).addValue("body", body);
        jdbc.update("""
                insert into document_templates (tenant_id, code, lang, title, body, updated_at)
                values (:t, :c, :l, :title, :body, now())
                on conflict (tenant_id, code, lang) do update
                  set title = excluded.title, body = excluded.body, updated_at = now()
                """, p);
        return get(code, lang);
    }
}
