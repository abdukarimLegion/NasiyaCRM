package uz.installment.contract;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uz.installment.audit.AuditLogRepository;
import uz.installment.client.Client;
import uz.installment.client.ClientRepository;
import uz.installment.common.BusinessException;
import uz.installment.contract.ContractDtos.CreateContractRequest;
import uz.installment.contract.ContractDtos.QuoteRequest;
import uz.installment.payment.Payment;
import uz.installment.payment.PaymentMethod;
import uz.installment.payment.PaymentRepository;
import uz.installment.payment.PaymentService;
import uz.installment.product.Category;
import uz.installment.product.CategoryRepository;
import uz.installment.product.Product;
import uz.installment.product.ProductRepository;
import uz.installment.scoring.ScoringEngine.Factor;
import uz.installment.scoring.ScoringService;
import uz.installment.security.AuthUser;
import uz.installment.security.Role;
import uz.installment.security.User;
import uz.installment.security.UserRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Haqiqiy PostgreSQL (Testcontainers) bilan asosiy moliyaviy oqim:
 * kredit limiti, boshlang'ich to'lov va externalId idempotentligi.
 * Docker mavjud bo'lmasa testlar o'tkazib yuboriladi.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class ContractFlowIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private ContractService contracts;
    @Autowired
    private ClientRepository clients;
    @Autowired
    private ProductRepository products;
    @Autowired
    private CategoryRepository categories;
    @Autowired
    private PaymentRepository payments;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private UserRepository users;
    @Autowired
    private AuditLogRepository auditLogs;

    private AuthUser officer;
    private Client client;
    private Product product;

    @BeforeEach
    void setUp() {
        User admin = new User();
        admin.setUsername("it-admin");
        admin.setPasswordHash("not-used");
        admin.setFullName("Integration Admin");
        admin.setRole(Role.ADMIN);
        users.saveAndFlush(admin);
        officer = new AuthUser(admin.getId(), admin.getUsername(), Role.ADMIN, null, Role.ADMIN.defaultPermissions());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                officer, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        Category category = categories.findAllByOrderByIdAsc().get(0);
        product = new Product();
        product.setCategory(category);
        product.setName("IT test mahsulot");
        product.setPrice(new BigDecimal("10000000"));
        product.setMarkupPct(new BigDecimal("20"));
        product.setTermMin(6);
        product.setTermMax(24);
        product.setStock(5);
        products.saveAndFlush(product);

        client = clientWithIncome("2000000", "12345678901234");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void recordsDownPaymentAsPayment() {
        var created = create(client, terms(new BigDecimal("2000000")), false, null);

        assertEquals(0, created.getDownPayment().compareTo(new BigDecimal("2000000")));
        List<Payment> saved = payments.findByContractIdOrderByPaidAtDesc(created.getId());
        assertEquals(1, saved.size());
        assertEquals(0, saved.get(0).getAmount().compareTo(new BigDecimal("2000000")));
        assertEquals(PaymentMethod.CASH, saved.get(0).getMethod());
        assertEquals("Boshlang'ich to'lov", saved.get(0).getNote());
    }

    @Test
    void rejectsMonthlyPaymentAboveFreeCapacity() {
        Client lowIncome = clientWithIncome("1000000", "12345678901235");

        BusinessException e = assertThrows(BusinessException.class,
                () -> create(lowIncome, terms(BigDecimal.ZERO), false, null));
        assertEquals("OVER_CAPACITY", e.getCode());
    }

    @Test
    void adminCanOverrideLimitAndAuditIsWritten() {
        Client lowIncome = clientWithIncome("1000000", "12345678901236");

        var created = create(lowIncome, terms(BigDecimal.ZERO), true, "Ishonchli mijoz");
        assertNotNull(created.getId());

        var logs = auditLogs.findAllByOrderByIdDesc(PageRequest.of(0, 20)).getContent();
        assertTrue(logs.stream().anyMatch(l -> "CONTRACT_CREATE".equals(l.getAction())
                && created.getId().equals(l.getEntityId())));
        assertTrue(logs.stream().anyMatch(l -> "CONTRACT_LIMIT_OVERRIDE".equals(l.getAction())
                && created.getId().equals(l.getEntityId())));
    }

    @Test
    void externalIdCannotBeReusedForAnotherContract() {
        var first = create(client, terms(BigDecimal.ZERO), false, null);
        Client secondClient = clientWithIncome("5000000", "12345678901237");
        var second = create(secondClient, terms(BigDecimal.ZERO), false, null);

        paymentService.register(new PaymentService.RegisterPayment(first.getId(), new BigDecimal("100000"),
                PaymentMethod.CASH, "ext-1", null), officer);

        BusinessException e = assertThrows(BusinessException.class, () -> paymentService.register(
                new PaymentService.RegisterPayment(second.getId(), new BigDecimal("100000"), PaymentMethod.CASH,
                        "ext-1", null), officer));
        assertEquals("EXTERNAL_ID_CONFLICT", e.getCode());
    }

    private Client clientWithIncome(String income, String pinfl) {
        Client c = new Client();
        c.setFullName("IT test mijoz");
        c.setPinfl(pinfl);
        c.setPhone("+998901234567");
        c.setMonthlyIncome(new BigDecimal(income));
        return clients.saveAndFlush(c);
    }

    private Contract create(Client c, QuoteRequest terms, boolean override, String reason) {
        var scoring = new ScoringService.ScoringRequest(Map.of(
                Factor.INCOME, 27, Factor.TENURE, 13, Factor.FAMILY, 9,
                Factor.HISTORY, 18, Factor.DISCIPLINE, 14, Factor.GUARANTOR, 9),
                true, true, Set.of());
        var score = uz.installment.scoring.ScoringEngine.evaluate(
                new uz.installment.scoring.ScoringEngine.Input(scoring.points(), true, true, Set.of()));
        var req = new CreateContractRequest(c.getId(), terms, scoring, null, null,
                PaymentMethod.CASH, override ? Boolean.TRUE : null, reason, null);
        return contracts.create(req, officer, score, null);
    }

    private QuoteRequest terms(BigDecimal downPayment) {
        return new QuoteRequest(product.getId(), null, null, downPayment, null, 12,
                LocalDate.now().plusMonths(1));
    }
}
