package uz.installment.contract;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import uz.installment.security.Role;
import uz.installment.security.UserRepository;
import uz.installment.settings.SettingsService;
import uz.installment.settings.TenantSettings;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ContractService {

    private final ContractRepository contracts;
    private final ClientRepository clients;
    private final ProductRepository products;
    private final UserRepository users;
    private final ScoringService scoring;
    private final SettingsService settings;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Transactional(readOnly = true)
    public MurabahaCalculator.Quote quote(QuoteRequest req) {
        Product p = products.findById(req.productId()).orElseThrow(() -> new NotFoundException("Mahsulot", req.productId()));
        return calculate(p, req, settings.current());
    }

    @Transactional
    public ContractDetails create(CreateContractRequest req, AuthUser officer) {
        Client client = clients.findById(req.clientId())
                .orElseThrow(() -> new NotFoundException("Mijoz", req.clientId()));
        Product product = products.findForUpdate(req.terms().productId())
                .orElseThrow(() -> new NotFoundException("Mahsulot", req.terms().productId()));
        if (!product.isActive()) {
            throw new BusinessException("PRODUCT_INACTIVE", "Mahsulot sotuvda emas");
        }
        if (product.getStock() <= 0) {
            throw new BusinessException("OUT_OF_STOCK", "Mahsulot omborda qolmagan");
        }

        ScoringEngine.Result score = scoring.evaluate(client, req.scoring());
        if (score.decision() == ScoringEngine.Decision.REJECTED) {
            throw new BusinessException("SCORING_REJECTED",
                    "Nasiya rad etildi: ball " + score.total() + ", stop-faktorlar " + score.stopFactors());
        }
        if (score.decision() == ScoringEngine.Decision.REVIEW && officer.role() != Role.ADMIN) {
            throw new BusinessException("REVIEW_REQUIRED",
                    "Risk toifasi C — shartnomani faqat administrator tasdiqlay oladi");
        }

        TenantSettings cfg = settings.current();
        MurabahaCalculator.Quote q = calculate(product, req.terms(), cfg);

        Contract c = new Contract();
        c.setContractNo(nextContractNo(cfg));
        c.setClient(client);
        c.setProduct(product);
        c.setProductName(product.getName());
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
        product.setStock(product.getStock() - 1);

        Contract saved = contracts.save(c);
        events.publishEvent(new ContractCreatedEvent(saved.getId()));
        return ContractDetails.of(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<ContractListItem> search(ContractStatus status, String q, int page, int size) {
        var p = contracts.search(status, q == null ? "" : q.trim(),
                PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "id")));
        return PageResponse.of(p, ContractListItem::of);
    }

    @Transactional(readOnly = true)
    public ContractDetails details(Long id) {
        return ContractDetails.of(contracts.findWithSchedule(id).orElseThrow(() -> new NotFoundException("Shartnoma", id)));
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
        if (c.getProduct() != null) {
            c.getProduct().setStock(c.getProduct().getStock() + 1);
        }
        return ContractDetails.of(c);
    }

    private MurabahaCalculator.Quote calculate(Product p, QuoteRequest req, TenantSettings cfg) {
        if (req.termMonths() < p.getTermMin() || req.termMonths() > p.getTermMax()) {
            throw new BusinessException("TERM_OUT_OF_RANGE",
                    "Muddat " + p.getTermMin() + "–" + p.getTermMax() + " oy oralig'ida bo'lishi kerak");
        }
        LocalDate firstDue = req.firstDueDate() != null ? req.firstDueDate() : LocalDate.now(clock).plusMonths(1);
        if (firstDue.isBefore(LocalDate.now(clock))) {
            throw new BusinessException("FIRST_DUE_IN_PAST", "Birinchi to'lov sanasi o'tib ketgan bo'lmasligi kerak");
        }
        try {
            return MurabahaCalculator.calculate(
                    req.price() != null ? req.price() : p.getPrice(),
                    req.downPayment(),
                    req.markupPct() != null ? req.markupPct() : p.getMarkupPct(),
                    req.termMonths(), firstDue, cfg.getRoundingStep());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("INVALID_TERMS", e.getMessage());
        }
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
