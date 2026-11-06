package uz.installment.document;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.installment.client.Client;
import uz.installment.collection.CollectionAction;
import uz.installment.collection.CollectionActionRepository;
import uz.installment.common.BusinessException;
import uz.installment.common.NotFoundException;
import uz.installment.common.TenantContext;
import uz.installment.contract.Contract;
import uz.installment.contract.ContractRepository;
import uz.installment.contract.ContractService;
import uz.installment.contract.ScheduleItem;
import uz.installment.contract.ScheduleStatus;
import uz.installment.document.DocumentTemplateService.Code;
import uz.installment.notification.TemplateRenderer;
import uz.installment.security.AuthUser;
import uz.installment.settings.SettingsService;
import uz.installment.settings.TenantSettings;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Shablon + shartnoma ma'lumotlari → PDF. Talabnoma va da'vo arizasi jurnalga yoziladi. */
@Service
@RequiredArgsConstructor
public class DocumentService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    /** Talabnomada to'lov uchun beriladigan muddat (kun) */
    private static final int DEMAND_DAYS = 10;

    public record Pdf(String fileName, byte[] bytes) {
    }

    public record LogRow(Long id, Code code, String docNo, String lang, BigDecimal amount, String userName,
                         Instant createdAt) {
    }

    private final ContractRepository contracts;
    private final DocumentTemplateService templates;
    private final SettingsService settings;
    private final CollectionActionRepository actions;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Pdf contract(Long contractId, String lang, AuthUser me) {
        Contract c = load(contractId, me);
        Map<String, String> vars = vars(c, lang, null);
        var tpl = templates.get(Code.CONTRACT, lang);
        boolean ru = "ru".equals(lang);
        List<List<String>> rows = new ArrayList<>();
        for (ScheduleItem s : c.getSchedule()) {
            rows.add(List.of(String.valueOf(s.getSeq()), DATE.format(s.getDueDate()), money(s.getAmount(), lang),
                    money(s.getPrincipalPart(), lang), money(s.getMarkupPart(), lang)));
        }
        var table = new PdfRenderer.Table(
                ru ? List.of("№", "Дата", "Платёж", "Основная часть", "Наценка")
                        : List.of("№", "Sana", "To'lov", "Asosiy qism", "Ustama"),
                rows, new float[]{0.6f, 1.6f, 2f, 2f, 2f}, new int[]{2, 3, 4});
        TenantSettings cfg = settings.current();
        var doc = new PdfRenderer.Doc(PdfRenderer.fill(tpl.title(), vars),
                header(cfg, c, ru, (ru ? "Дата: " : "Sana: ") + DATE.format(signedDate(c))),
                PdfRenderer.fill(tpl.body(), vars), table,
                List.of((ru ? "Продавец" : "Sotuvchi") + "\n" + company(cfg),
                        (ru ? "Покупатель" : "Xaridor") + "\n" + c.getClient().getFullName()),
                null);
        return new Pdf(c.getContractNo() + ".pdf", PdfRenderer.render(doc));
    }

    /** Talabnoma (DEMAND) yoki da'vo arizasi (CLAIM): raqam beriladi, jurnalga va undirish tarixiga yoziladi. */
    @Transactional
    public Pdf issue(Long contractId, Code code, String lang, AuthUser me) {
        if (code == Code.CONTRACT) {
            throw new IllegalArgumentException("Shartnoma uchun /contract ishlatiladi");
        }
        Contract c = load(contractId, me);
        Overdue od = overdue(c);
        if (od.amount().signum() <= 0) {
            throw new BusinessException("NO_OVERDUE", "Shartnoma bo'yicha muddati o'tgan qarz yo'q");
        }
        long seq = jdbc.getJdbcTemplate().queryForObject("select nextval('document_no_seq')", Long.class);
        String docNo = (code == Code.DEMAND ? "T" : "D") + "-" + LocalDate.now(clock).getYear() + "/"
                + String.format("%05d", seq);
        Map<String, String> vars = vars(c, lang, docNo);
        var tpl = templates.get(code, lang);
        TenantSettings cfg = settings.current();
        boolean ru = "ru".equals(lang);
        String footnote = ru
                ? "По условиям договора мурабаха проценты, штрафы и пени за просрочку не начисляются."
                : "Murobaha shartnomasi shartlariga ko'ra kechikish uchun foiz, jarima va penya hisoblanmaydi.";
        var doc = new PdfRenderer.Doc(PdfRenderer.fill(tpl.title(), vars),
                header(cfg, c, ru, (ru ? "Исх. № " : "Chiqish № ") + docNo + " · " + DATE.format(LocalDate.now(clock))),
                PdfRenderer.fill(tpl.body(), vars), null,
                List.of(company(cfg) + "\n" + (ru ? "Уполномоченное лицо" : "Vakolatli shaxs")), footnote);
        byte[] bytes = PdfRenderer.render(doc);

        BigDecimal amount = code == Code.DEMAND ? od.amount() : remaining(c);
        jdbc.update("""
                insert into document_log (tenant_id, contract_id, code, doc_no, lang, amount, user_id)
                values (:t, :c, :code, :no, :lang, :amount, :u)
                """, new MapSqlParameterSource().addValue("t", TenantContext.currentTenantId())
                .addValue("c", c.getId()).addValue("code", code.name()).addValue("no", docNo)
                .addValue("lang", lang).addValue("amount", amount).addValue("u", me.id()));
        CollectionAction a = new CollectionAction();
        a.setContractId(c.getId());
        a.setActionType(code == Code.DEMAND ? CollectionAction.Type.LETTER : CollectionAction.Type.LEGAL);
        a.setResult(code.name());
        a.setNote(docNo);
        a.setUserId(me.id());
        actions.save(a);
        return new Pdf(docNo.replace('/', '-') + ".pdf", bytes);
    }

    @Transactional(readOnly = true)
    public List<LogRow> log(Long contractId, AuthUser me) {
        load(contractId, me);
        return jdbc.query("""
                select d.id, d.code, d.doc_no, d.lang, d.amount, u.full_name, d.created_at
                from document_log d left join users u on u.id = d.user_id
                where d.contract_id = :c order by d.id desc
                """, Map.of("c", contractId), (rs, i) -> new LogRow(rs.getLong("id"),
                Code.valueOf(rs.getString("code")), rs.getString("doc_no"), rs.getString("lang"),
                rs.getBigDecimal("amount"), rs.getString("full_name"), rs.getTimestamp("created_at").toInstant()));
    }

    // ------------------------------------------------------------------ yordamchilar

    private record Overdue(BigDecimal amount, long days) {
    }

    private Contract load(Long id, AuthUser me) {
        Contract c = contracts.findWithSchedule(id).orElseThrow(() -> new NotFoundException("Shartnoma", id));
        ContractService.checkScope(c, me);
        return c;
    }

    private Overdue overdue(Contract c) {
        LocalDate today = LocalDate.now(clock);
        BigDecimal sum = BigDecimal.ZERO;
        LocalDate oldest = null;
        for (ScheduleItem s : c.getSchedule()) {
            if (s.getStatus() != ScheduleStatus.PAID && s.getDueDate().isBefore(today)) {
                sum = sum.add(s.getAmount().subtract(s.getPaidAmount()));
                if (oldest == null || s.getDueDate().isBefore(oldest)) oldest = s.getDueDate();
            }
        }
        return new Overdue(sum, oldest == null ? 0 : ChronoUnit.DAYS.between(oldest, today));
    }

    private static BigDecimal remaining(Contract c) {
        return c.getSchedule().stream().map(s -> s.getAmount().subtract(s.getPaidAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Map<String, String> vars(Contract c, String lang, String docNo) {
        TenantSettings cfg = settings.current();
        Client cl = c.getClient();
        BigDecimal remaining = remaining(c);
        BigDecimal paid = c.getInstallmentTotal().subtract(remaining);
        Overdue od = overdue(c);
        Map<String, String> v = new LinkedHashMap<>();
        v.put("company", company(cfg));
        v.put("client", cl.getFullName());
        v.put("pinfl", cl.getPinfl());
        v.put("passport", Objects.toString(cl.getPassportSeries(), "—"));
        v.put("address", Stream.of(cl.getRegion(), cl.getDistrict(), cl.getAddress())
                .filter(s -> s != null && !s.isBlank()).collect(Collectors.joining(", ")));
        v.put("phone", cl.getPhone());
        v.put("contract_no", c.getContractNo());
        v.put("contract_date", DATE.format(signedDate(c)));
        v.put("product", c.getProductName());
        v.put("sale_price", money(c.getSalePrice(), lang));
        v.put("down_payment", money(c.getDownPayment(), lang));
        v.put("installment_total", money(c.getInstallmentTotal(), lang));
        v.put("monthly", money(c.getMonthlyPayment(), lang));
        v.put("term", String.valueOf(c.getTermMonths()));
        v.put("paid", money(paid.add(c.getDownPayment()), lang));
        v.put("remaining", money(remaining, lang));
        v.put("overdue", money(od.amount(), lang));
        v.put("days_late", String.valueOf(od.days()));
        v.put("deadline", DATE.format(LocalDate.now(clock).plusDays(DEMAND_DAYS)));
        v.put("doc_no", docNo == null ? c.getContractNo() : docNo);
        v.put("today", DATE.format(LocalDate.now(clock)));
        v.put("branch", c.getBranch() != null ? c.getBranch().getName()
                + (c.getBranch().getPhone() != null ? " (" + c.getBranch().getPhone() + ")" : "")
                : company(cfg));
        return v;
    }

    private List<String> header(TenantSettings cfg, Contract c, boolean ru, String extra) {
        List<String> lines = new ArrayList<>();
        lines.add(company(cfg));
        if (c.getBranch() != null) {
            lines.add(c.getBranch().getName() + (c.getBranch().getAddress() != null ? ", " + c.getBranch().getAddress() : ""));
        }
        lines.add(extra);
        return lines;
    }

    private LocalDate signedDate(Contract c) {
        Instant at = c.getSignedAt() != null ? c.getSignedAt() : c.getCreatedAt();
        return at == null ? LocalDate.now(clock) : LocalDate.ofInstant(at, clock.getZone());
    }

    private static String company(TenantSettings cfg) {
        return cfg.getCompanyName() != null && !cfg.getCompanyName().isBlank() ? cfg.getCompanyName() : cfg.getBrandName();
    }

    private static String money(BigDecimal v, String lang) {
        return TemplateRenderer.money(v, lang);
    }
}
