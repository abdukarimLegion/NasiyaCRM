package uz.installment.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.installment.client.Client;
import uz.installment.client.ClientRepository;
import uz.installment.common.BusinessException;
import uz.installment.common.NotFoundException;
import uz.installment.common.PageResponse;
import uz.installment.contract.Contract;
import uz.installment.contract.ContractDtos.ContractDetails;
import uz.installment.contract.ContractDtos.CreateContractRequest;
import uz.installment.contract.ContractService;
import uz.installment.contract.MurabahaCalculator;
import uz.installment.scoring.ScoringEngine;
import uz.installment.scoring.ScoringService;
import uz.installment.security.AuthUser;
import uz.installment.security.Permission;
import uz.installment.security.UserRepository;
import uz.installment.partner.Partner;
import uz.installment.partner.PartnerLimitCalculator;
import uz.installment.partner.PartnerRepository;
import uz.installment.partner.PartnerService;
import uz.installment.verification.VerificationService;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Nasiya arizasi: yuborish → skoring → (tasdiqlash) → shartnoma.
 * Rad etilgan arizalar ham saqlanadi — KPI (konversiya) va tahlil uchun.
 */
@Service
@RequiredArgsConstructor
public class ApplicationService {

    private final ApplicationRepository applications;
    private final ClientRepository clients;
    private final UserRepository users;
    private final ScoringService scoring;
    private final ContractService contracts;
    private final ObjectMapper json;
    private final VerificationService verification;
    private final uz.installment.mib.MibService mib;
    private final PartnerRepository partners;
    private final uz.installment.partner.gateway.PartnerGateways gateways;
    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Arizani qabul qiladi va skoring qiladi.
     * <ul>
     *   <li>REJECTED (stop-faktor yoki D toifa) — ariza REJECTED holatida saqlanadi;</li>
     *   <li>REVIEW (C toifa) — tasdiqlash huquqi bor xodim yuborsa avtomatik APPROVED, aks holda PENDING;</li>
     *   <li>APPROVED — shartnomaga tayyor.</li>
     * </ul>
     * {@code contractNow} bo'lsa va ariza APPROVED bo'lsa, shartnoma darhol tuziladi.
     */
    @Transactional
    public Application submit(CreateContractRequest req, AuthUser officer, boolean contractNow) {
        Client client = clients.findById(req.clientId())
                .orElseThrow(() -> new NotFoundException("Mijoz", req.clientId()));
        // Savat va shartlar noto'g'ri bo'lsa ariza ochilmaydi (422) — rad etish skoringdan keyin
        ContractService.Prepared prep = contracts.prepare(req.terms(), false);
        MurabahaCalculator.Quote q = prep.quote();
        // MIB'da qarzdorlik: amaldagi natija bo'lmasa avtomatik tekshiriladi (REQUIRED rejimda natijasiz ariza yuborilmaydi)
        mib.ensureForApplication(client, officer.id());
        ScoringEngine.Result score = scoring.evaluate(client, req.scoring());
        // Sozlamada talab qilingan bo'lsa, mijoz tasdiqlangan bo'lishi shart (rad etilgan ariza uchun ham tekshiramiz)
        String verifiedBy = verification.requireFor(client.getId());

        Application a = new Application();
        a.setApplicationNo(String.format("AR-%d-%05d", java.time.Year.now().getValue(), applications.nextNo()));
        a.setClient(client);
        a.setBranch(contracts.resolveBranch(req.branchId(), officer));
        a.setOfficer(users.getReferenceById(officer.id()));
        a.setRequest(json.convertValue(req, new TypeReference<LinkedHashMap<String, Object>>() {
        }));
        a.setItemsSummary(prep.summary());
        a.setCostPrice(q.costPrice());
        a.setDownPayment(q.downPayment());
        a.setMarkupPct(q.markupPct());
        a.setSalePrice(q.salePrice());
        a.setInstallmentTotal(q.installmentTotal());
        a.setTermMonths(q.termMonths());
        a.setMonthlyPayment(q.monthlyPayment());
        a.setScoreTotal(score.total());
        a.setRiskCategory(score.risk());
        a.setDecision(score.decision());
        a.setScoreDetails(details(score, req.scoring()));

        switch (score.decision()) {
            case REJECTED -> {
                a.setStatus(ApplicationStatus.REJECTED);
                a.setRejectionReason(rejectionText(score));
                a.setDecidedAt(Instant.now());
            }
            case REVIEW -> {
                if (officer.has(Permission.CONTRACTS_APPROVE)) {
                    markApproved(a, officer);
                } else {
                    a.setStatus(ApplicationStatus.PENDING);
                }
            }
            default -> a.setStatus(ApplicationStatus.APPROVED);
        }
        applications.save(a);
        if (verifiedBy != null) {
            jdbc.update("update applications set verification_methods = :m, verified_at = now() where id = :id",
                    Map.of("m", verifiedBy, "id", a.getId()));
        }
        if (contractNow && a.getStatus() == ApplicationStatus.APPROVED) {
            convert(a, officer, score);
        }
        return a;
    }

    @Transactional
    public Application approve(Long id, AuthUser me) {
        Application a = applications.findForUpdate(id).orElseThrow(() -> new NotFoundException("Ariza", id));
        checkScope(a, me);
        if (a.getStatus() != ApplicationStatus.PENDING) {
            throw new BusinessException("APPLICATION_STATE", "Faqat kutilayotgan arizani tasdiqlash mumkin");
        }
        markApproved(a, me);
        return applications.findDetailed(id).orElseThrow();
    }

    @Transactional
    public Application reject(Long id, String reason, AuthUser me) {
        Application a = applications.findForUpdate(id).orElseThrow(() -> new NotFoundException("Ariza", id));
        checkScope(a, me);
        if (a.getStatus() != ApplicationStatus.PENDING && a.getStatus() != ApplicationStatus.APPROVED) {
            throw new BusinessException("APPLICATION_STATE", "Bu arizani rad etib bo'lmaydi");
        }
        a.setStatus(ApplicationStatus.REJECTED);
        a.setRejectionReason(reason == null || reason.isBlank() ? "Xodim tomonidan rad etildi" : reason.trim());
        a.setDecidedBy(users.getReferenceById(me.id()));
        a.setDecidedAt(Instant.now());
        return applications.findDetailed(id).orElseThrow();
    }

    /** Tasdiqlangan arizadan shartnoma tuzadi. */
    @Transactional
    public Application toContract(Long id, AuthUser me) {
        Application a = applications.findForUpdate(id).orElseThrow(() -> new NotFoundException("Ariza", id));
        checkScope(a, me);
        if (a.getStatus() != ApplicationStatus.APPROVED) {
            throw new BusinessException("APPLICATION_STATE", "Shartnoma faqat tasdiqlangan arizadan tuziladi");
        }
        CreateContractRequest req = json.convertValue(a.getRequest(), CreateContractRequest.class);
        // Ariza bilan shartnoma orasida mijoz holati o'zgargan bo'lishi mumkin (masalan, kechikish) — qayta tekshiramiz
        ScoringEngine.Result score = scoring.evaluate(a.getClient(), req.scoring());
        if (score.decision() == ScoringEngine.Decision.REJECTED) {
            throw new BusinessException("SCORING_REJECTED",
                    "Mijoz holati o'zgargan, shartnoma tuzib bo'lmaydi: " + rejectionText(score));
        }
        convert(a, me, score);
        return applications.findDetailed(id).orElseThrow();
    }

    /**
     * Tasdiqlangan arizani hamkor provayderga topshiradi: shartnoma tuzilmaydi (qarz hamkorda), tovar ombordan
     * chiqariladi, komissiya yoziladi. Hamkor shartlari (muddat, boshlang'ich to'lov, limit) tekshiriladi.
     */
    @Transactional
    public Application handover(Long id, String partnerCode, String txId, String smsCode, String notes,
                                java.util.List<Map<String, Object>> serials, AuthUser me) {
        Application a = applications.findForUpdate(id).orElseThrow(() -> new NotFoundException("Ariza", id));
        checkScope(a, me);
        PartnerDeal deal = partnerDeal(a, partnerCode);
        Partner p = deal.partner();
        var gateway = gateways.forCode(p.getCode());
        boolean hasCode = smsCode != null && !smsCode.isBlank();
        // Haqiqiy integratsiyada hamkor tasdig'isiz tovar topshirilmaydi; mock'da kod ixtiyoriy
        if (!gateway.isMock() && !hasCode) {
            throw new BusinessException("PARTNER_CODE_REQUIRED", "Hamkor yuborgan tasdiqlash kodini kiriting");
        }
        if (hasCode) {
            if (txId == null || txId.isBlank()) {
                throw new BusinessException("PARTNER_TX_REQUIRED", "Avval hamkor tizimida buyurtma oching");
            }
            var res = gateway.confirmOrder(p.getCode(), txId.trim(), smsCode);
            if (res.status() != uz.installment.partner.gateway.PartnerGateway.OrderStatus.CONFIRMED) {
                throw new BusinessException("PARTNER_CONFIRM_FAILED", p.getName() + ": "
                        + (res.message() == null ? "savdo tasdiqlanmadi" : res.message()));
            }
        }
        contracts.reserveStock(deal.request().terms());
        a.setProvider(p.getCode());
        a.setPartner(p);
        a.setPartnerTxId(txId == null || txId.isBlank() ? null : txId.trim());
        a.setPartnerNotes(notes);
        a.setPartnerSerials(serials == null || serials.isEmpty() ? null : serials);
        var offer = deal.offer();
        a.setPartnerHandoverAt(Instant.now());
        a.setPartnerCommission(offer.commission());
        a.setStatus(ApplicationStatus.PARTNER);
        return applications.findDetailed(id).orElseThrow();
    }

    /** Hamkor tizimida buyurtma ochish natijasi (mijozga tasdiqlash kodi yuboriladi). */
    public record PartnerOrder(String partnerCode, String partnerName, String transactionId, boolean mock,
                               String message) {
    }

    private record PartnerDeal(Partner partner, CreateContractRequest request, ContractService.Prepared prepared,
                               PartnerLimitCalculator.Offer offer) {
    }

    /** Ariza hamkorga topshirilishi mumkinmi: holat, hamkor faolligi va hamkor shartlari (muddat, boshlang'ich to'lov, limit). */
    private PartnerDeal partnerDeal(Application a, String partnerCode) {
        if (a.getStatus() != ApplicationStatus.APPROVED) {
            throw new BusinessException("APPLICATION_STATE", "Hamkorga faqat tasdiqlangan ariza topshiriladi");
        }
        Partner p = partners.findByCodeIgnoreCase(partnerCode)
                .orElseThrow(() -> new NotFoundException("Hamkor", partnerCode));
        if (!p.isActive()) {
            throw new BusinessException("PARTNER_INACTIVE", "Hamkor faol emas: " + p.getName());
        }
        CreateContractRequest req = json.convertValue(a.getRequest(), CreateContractRequest.class);
        ContractService.Prepared prep = contracts.prepare(req.terms(), false);
        var offer = PartnerLimitCalculator.offer(PartnerService.toTerms(p), a.getClient().getMonthlyIncome(),
                a.getRiskCategory(), prep.quote().costPrice(), prep.quote().costPrice().subtract(prep.quote().downPayment()),
                prep.quote().downPayment(), prep.quote().termMonths());
        if (!offer.eligible()) {
            throw new BusinessException("PARTNER_" + offer.reason(),
                    p.getName() + " shartlariga mos kelmaydi (" + offer.reason() + ")");
        }
        return new PartnerDeal(p, req, prep, offer);
    }

    /**
     * Hamkor tizimida savdo buyurtmasini ochadi: hamkor mijozga tasdiqlash kodi yuboradi. Ariza holati
     * o'zgarmaydi — tovar {@link #handover} da kod tasdiqlangach topshiriladi.
     */
    @Transactional
    public PartnerOrder partnerOrder(Long id, String partnerCode, AuthUser me) {
        Application a = applications.findForUpdate(id).orElseThrow(() -> new NotFoundException("Ariza", id));
        checkScope(a, me);
        PartnerDeal deal = partnerDeal(a, partnerCode);
        Partner p = deal.partner();
        var gateway = gateways.forCode(p.getCode());
        var q = deal.prepared().quote();
        var res = gateway.createOrder(new uz.installment.partner.gateway.PartnerGateway.OrderRequest(p.getCode(),
                a.getClient().getPinfl(), a.getClient().getPhone(), a.getApplicationNo(), q.costPrice(),
                q.downPayment(), q.termMonths(), deal.prepared().lines().stream()
                .map(l -> new uz.installment.partner.gateway.PartnerGateway.OrderItem(l.product().getName(),
                        l.quantity(), l.unitPrice())).toList()));
        if (res.status() != uz.installment.partner.gateway.PartnerGateway.OrderStatus.CODE_SENT) {
            throw new BusinessException("PARTNER_ORDER_FAILED", p.getName() + ": "
                    + (res.message() == null ? "buyurtma ochilmadi" : res.message()));
        }
        return new PartnerOrder(p.getCode(), p.getName(), res.transactionId(), gateway.isMock(), res.message());
    }

    @Transactional(readOnly = true)
    public PageResponse<Application> search(ApplicationStatus status, String q, Long branchId, Long officerId,
                                            int page, int size) {
        var p = applications.search(status, branchId, officerId, q == null ? "" : q.trim(),
                PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "id")));
        return new PageResponse<>(p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
    }

    /** Tranzaksiyadan keyin: barcha bog'liq obyektlari yuklangan holda (controller'da lazy xato bo'lmasligi uchun). */
    @Transactional(readOnly = true)
    public Application reload(Long id) {
        return applications.findDetailed(id).orElseThrow(() -> new NotFoundException("Ariza", id));
    }

    @Transactional(readOnly = true)
    public Application get(Long id, AuthUser me) {
        Application a = applications.findDetailed(id).orElseThrow(() -> new NotFoundException("Ariza", id));
        checkScope(a, me);
        return a;
    }

    /** Boshqa filial arizasi — faqat ALL_BRANCHES ruxsati bilan ko'rinadi va boshqariladi. */
    static void checkScope(Application a, AuthUser me) {
        Long scope = me.branchScope(null);
        if (scope != null && (a.getBranch() == null || !scope.equals(a.getBranch().getId()))) {
            throw new NotFoundException("Ariza", a.getId());
        }
    }

    @Transactional(readOnly = true)
    public Map<ApplicationStatus, Long> counts(Long branchId) {
        Map<ApplicationStatus, Long> m = new java.util.EnumMap<>(ApplicationStatus.class);
        for (ApplicationStatus s : ApplicationStatus.values()) {
            m.put(s, 0L);
        }
        applications.countByStatus(branchId).forEach(c -> m.put(c.getStatus(), c.getCnt()));
        return m;
    }

    @Transactional(readOnly = true)
    public ContractDetails contractOf(Application a, AuthUser me) {
        return a.getContract() == null ? null : contracts.details(a.getContract().getId(), me);
    }

    private void markApproved(Application a, AuthUser by) {
        a.setStatus(ApplicationStatus.APPROVED);
        a.setDecidedBy(users.getReferenceById(by.id()));
        a.setDecidedAt(Instant.now());
    }

    private void convert(Application a, AuthUser officer, ScoringEngine.Result score) {
        CreateContractRequest req = json.convertValue(a.getRequest(), CreateContractRequest.class);
        Contract c = contracts.create(req, officer, score, a.getId());
        a.setContract(c);
        a.setStatus(ApplicationStatus.CONTRACTED);
    }

    static String rejectionText(ScoringEngine.Result r) {
        return r.stopFactors().isEmpty()
                ? "Ball " + r.total() + " — risk toifasi " + r.risk()
                : "Ball " + r.total() + ", stop-faktorlar: " + r.stopFactors();
    }

    private static Map<String, Object> details(ScoringEngine.Result r, ScoringService.ScoringRequest req) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("points", r.points());
        m.put("inputPoints", req.points());
        m.put("incomeVerified", req.incomeVerified());
        m.put("guarantorPresent", req.guarantorPresent());
        m.put("stopFactors", r.stopFactors());
        return m;
    }



}
