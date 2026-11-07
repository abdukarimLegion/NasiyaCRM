package uz.installment.contract;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.installment.branch.Branch;
import uz.installment.branch.BranchRepository;
import uz.installment.client.Client;
import uz.installment.client.ClientRepository;
import uz.installment.common.BusinessException;
import uz.installment.common.NotFoundException;
import uz.installment.common.PageResponse;
import uz.installment.contract.ContractDtos.ContractDetails;
import uz.installment.contract.ContractDtos.ContractListItem;
import uz.installment.contract.ContractDtos.CreateContractRequest;
import uz.installment.contract.ContractDtos.QuoteRequest;
import uz.installment.product.Product;
import uz.installment.product.ProductRepository;
import uz.installment.scoring.ScoringEngine;
import uz.installment.scoring.ScoringService;
import uz.installment.security.AuthUser;
import uz.installment.security.Permission;
import uz.installment.security.UserRepository;
import uz.installment.settings.SettingsService;
import uz.installment.settings.TenantSettings;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ContractService {

    private final ContractRepository contracts;
    private final ScheduleItemRepository scheduleItems;
    private final ClientRepository clients;
    private final ProductRepository products;
    private final UserRepository users;
    private final BranchRepository branches;
    private final SettingsService settings;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /** Savatdagi bitta qator (mahsulot, miqdor, birlik narxi). */
    public record Line(Product product, int quantity, BigDecimal unitPrice, BigDecimal total) {
    }

    /** Hisoblangan savat: qatorlar, murobaha hisob-kitobi va qisqa tavsif (ro'yxatlar uchun). */
    public record Prepared(List<Line> lines, MurabahaCalculator.Quote quote, String summary) {
    }

    @Transactional(readOnly = true)
    public MurabahaCalculator.Quote quote(QuoteRequest req) {
        return prepare(req, false).quote();
    }

    /**
     * Savatni tekshiradi va hisoblaydi. {@code lock} — ombor qoldig'ini kamaytirishdan oldin mahsulotlarni
     * bloklaydi (parallel shartnomalar bir-birini buzmasligi uchun; deadlock bo'lmasligi uchun id bo'yicha tartiblanadi).
     */
    @Transactional
    public Prepared prepare(QuoteRequest req, boolean lock) {
        // Bir xil mahsulot ikki marta kelsa — miqdorlari qo'shiladi
        Map<Long, int[]> qty = new TreeMap<>();
        Map<Long, BigDecimal> priceOverride = new HashMap<>();
        if (req.items() != null && !req.items().isEmpty()) {
            for (ContractDtos.CartItem it : req.items()) {
                qty.computeIfAbsent(it.productId(), k -> new int[1])[0] += it.quantity();
                if (it.unitPrice() != null) {
                    priceOverride.put(it.productId(), it.unitPrice());
                }
            }
        } else if (req.productId() != null) {
            qty.put(req.productId(), new int[]{1});
            if (req.price() != null) {
                priceOverride.put(req.productId(), req.price());
            }
        } else {
            throw new BusinessException("CART_EMPTY", "Kamida bitta mahsulot tanlang");
        }

        List<Line> lines = new java.util.ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal markupWeighted = BigDecimal.ZERO;
        int termMin = 1;
        int termMax = 60;
        for (Map.Entry<Long, int[]> e : qty.entrySet()) {
            Product p = (lock ? products.findForUpdate(e.getKey()) : products.findById(e.getKey()))
                    .orElseThrow(() -> new NotFoundException("Mahsulot", e.getKey()));
            int n = e.getValue()[0];
            if (lock) {
                if (!p.isActive()) {
                    throw new BusinessException("PRODUCT_INACTIVE", "Mahsulot sotuvda emas: " + p.getName());
                }
                if (p.getStock() < n) {
                    throw new BusinessException("OUT_OF_STOCK",
                            "Omborda yetarli emas: " + p.getName() + " (qoldiq " + p.getStock() + ", kerak " + n + ")");
                }
            }
            BigDecimal unit = priceOverride.getOrDefault(e.getKey(), p.getPrice());
            BigDecimal line = unit.multiply(BigDecimal.valueOf(n));
            lines.add(new Line(p, n, unit, line));
            total = total.add(line);
            markupWeighted = markupWeighted.add(p.getMarkupPct().multiply(line));
            termMin = Math.max(termMin, p.getTermMin());
            termMax = Math.min(termMax, p.getTermMax());
        }
        if (termMin > termMax) {
            throw new BusinessException("TERM_CONFLICT",
                    "Savatdagi mahsulotlar uchun umumiy muddat yo'q (" + termMin + " > " + termMax + " oy)");
        }
        if (req.termMonths() < termMin || req.termMonths() > termMax) {
            throw new BusinessException("TERM_OUT_OF_RANGE",
                    "Muddat " + termMin + "–" + termMax + " oy oralig'ida bo'lishi kerak");
        }
        BigDecimal markup = req.markupPct() != null ? req.markupPct()
                : markupWeighted.divide(total, 2, RoundingMode.HALF_UP);

        TenantSettings cfg = settings.current();
        LocalDate firstDue = req.firstDueDate() != null ? req.firstDueDate() : LocalDate.now(clock).plusMonths(1);
        if (firstDue.isBefore(LocalDate.now(clock))) {
            throw new BusinessException("FIRST_DUE_IN_PAST", "Birinchi to'lov sanasi o'tib ketgan bo'lmasligi kerak");
        }
        MurabahaCalculator.Quote q;
        try {
            q = MurabahaCalculator.calculate(total, req.downPayment(), markup, req.termMonths(), firstDue,
                    cfg.getRoundingStep());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("INVALID_TERMS", ex.getMessage());
        }
        String summary = lines.stream()
                .map(l -> l.product().getName() + (l.quantity() > 1 ? " ×" + l.quantity() : ""))
                .collect(Collectors.joining(", "));
        if (summary.length() > 190) {
            summary = summary.substring(0, 187) + "...";
        }
        return new Prepared(lines, q, summary);
    }

    /**
     * Tasdiqlangan arizadan shartnoma tuzadi. Skoring va tasdiqlash qarori {@link uz.installment.application.ApplicationService}
     * da qabul qilingan; bu yerda faqat savat qayta tekshiriladi, ombor kamaytiriladi va jadval yoziladi.
     */
    @Transactional
    public Contract create(CreateContractRequest req, AuthUser officer, ScoringEngine.Result score, Long applicationId) {
        Client client = clients.findById(req.clientId())
                .orElseThrow(() -> new NotFoundException("Mijoz", req.clientId()));
        Prepared prep = prepare(req.terms(), true);
        MurabahaCalculator.Quote q = prep.quote();
        TenantSettings cfg = settings.current();

        Contract c = new Contract();
        c.setContractNo(nextContractNo(cfg));
        c.setClient(client);
        c.setApplicationId(applicationId);
        c.setProduct(prep.lines().get(0).product());
        c.setProductName(prep.summary());
        c.setCostPrice(q.costPrice());
        c.setDownPayment(q.downPayment());
        c.setMarkupPct(q.markupPct());
        c.setMarkupAmount(q.markupAmount());
        c.setSalePrice(q.salePrice());
        c.setInstallmentTotal(q.installmentTotal());
        c.setTermMonths(q.termMonths());
        c.setMonthlyPayment(q.monthlyPayment());
        c.setFirstDueDate(q.schedule().get(0).dueDate());
        c.setStatus(ContractStatus.ACTIVE);
        c.setScoreTotal(score.total());
        c.setRiskCategory(score.risk());
        c.setDecision(score.decision());
        c.setScoreDetails(scoreDetails(score, req.scoring()));
        c.setOfficer(users.getReferenceById(officer.id()));
        c.setBranch(resolveBranch(req.branchId(), officer));
        c.setGuarantorName(req.guarantorName());
        c.setGuarantorPhone(req.guarantorPhone());
        c.setSignedAt(Instant.now());
        for (MurabahaCalculator.Installment i : q.schedule()) {
            ScheduleItem s = new ScheduleItem();
            s.setSeq(i.seq());
            s.setDueDate(i.dueDate());
            s.setAmount(i.amount());
            s.setPrincipalPart(i.principalPart());
            s.setMarkupPart(i.markupPart());
            c.addScheduleItem(s);
        }
        for (Line l : prep.lines()) {
            ContractItem item = new ContractItem();
            item.setProduct(l.product());
            item.setProductName(l.product().getName());
            item.setUnitPrice(l.unitPrice());
            item.setQuantity(l.quantity());
            item.setLineTotal(l.total());
            c.addItem(item);
            l.product().setStock(l.product().getStock() - l.quantity());
        }
        Contract saved = contracts.save(c);
        events.publishEvent(new ContractCreatedEvent(saved.getId()));
        return saved;
    }

    /** Hamkorga topshirilgan savdo: shartnoma tuzilmaydi, lekin tovar ombordan chiqadi. */
    @Transactional
    public Prepared reserveStock(QuoteRequest terms) {
        Prepared prep = prepare(terms, true);
        for (Line l : prep.lines()) {
            l.product().setStock(l.product().getStock() - l.quantity());
        }
        return prep;
    }

    /** Filial: xodimning o'z filiali; filiali yo'q (bosh ofis) yoki barcha filiallarga ruxsati bo'lsa — tanlangani. */
    public Branch resolveBranch(Long requested, AuthUser officer) {
        Long branchId = officer.branchId() != null && !officer.has(Permission.ALL_BRANCHES)
                ? officer.branchId()
                : (requested != null ? requested : officer.branchId());
        return branchId == null ? null
                : branches.findById(branchId).orElseThrow(() -> new NotFoundException("Filial", branchId));
    }

    @Transactional(readOnly = true)
    public PageResponse<ContractListItem> search(ContractStatus status, ScoringEngine.RiskCategory risk, boolean openOnly,
                                                 String q, Long branchId, int page, int size) {
        var p = contracts.search(status, risk, openOnly, q == null ? "" : q.trim(), branchId,
                PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "id")));
        // Jadval yig'indilari bitta so'rov bilan (har bir shartnoma uchun alohida emas)
        List<Long> ids = p.getContent().stream().map(Contract::getId).toList();
        Map<Long, ScheduleItemRepository.ScheduleTotals> totals = ids.isEmpty() ? Map.of()
                : scheduleItems.totalsFor(ids).stream()
                .collect(Collectors.toMap(ScheduleItemRepository.ScheduleTotals::getContractId, Function.identity()));
        return PageResponse.of(p, c -> ContractListItem.of(c, totals.get(c.getId())));
    }

    @Transactional(readOnly = true)
    public ContractDetails details(Long id, AuthUser me) {
        Contract c = contracts.findWithSchedule(id).orElseThrow(() -> new NotFoundException("Shartnoma", id));
        checkScope(c, me);
        return ContractDetails.of(c);
    }

    /** Boshqa filial shartnomasi — faqat ALL_BRANCHES ruxsati bilan ko'rinadi. */
    public static void checkScope(Contract c, AuthUser me) {
        Long scope = me.branchScope(null);
        if (scope != null && (c.getBranch() == null || !scope.equals(c.getBranch().getId()))) {
            throw new NotFoundException("Shartnoma", c.getId());
        }
    }

    @Transactional
    public ContractDetails cancel(Long id) {
        Contract c = contracts.findForUpdate(id).orElseThrow(() -> new NotFoundException("Shartnoma", id));
        boolean anyPaid = c.getSchedule().stream().anyMatch(s -> s.getPaidAmount().signum() > 0);
        if (anyPaid) {
            throw new BusinessException("HAS_PAYMENTS", "To'lov qilingan shartnomani bekor qilib bo'lmaydi");
        }
        if (c.getStatus() == ContractStatus.CANCELLED) {
            return ContractDetails.of(c);
        }
        c.setStatus(ContractStatus.CANCELLED);
        if (c.getItems().isEmpty()) {
            if (c.getProduct() != null) {
                c.getProduct().setStock(c.getProduct().getStock() + 1);
            }
        }
        for (ContractItem item : c.getItems()) {
            if (item.getProduct() != null) {
                item.getProduct().setStock(item.getProduct().getStock() + item.getQuantity());
            }
        }
        return ContractDetails.of(c);
    }

    private String nextContractNo(TenantSettings cfg) {
        long seq = contracts.nextContractSeq();
        return String.format("%s-%d-%04d", cfg.getContractPrefix(), LocalDate.now(clock).getYear(), seq);
    }

    private static Map<String, Object> scoreDetails(ScoringEngine.Result r, ScoringService.ScoringRequest req) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("points", r.points());
        m.put("inputPoints", req.points());
        m.put("incomeVerified", req.incomeVerified());
        m.put("guarantorPresent", req.guarantorPresent());
        m.put("stopFactors", r.stopFactors());
        return m;
    }
}
