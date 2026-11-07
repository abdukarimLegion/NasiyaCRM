package uz.installment.partner;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.installment.client.Client;
import uz.installment.client.ClientRepository;
import uz.installment.client.ClientService;
import uz.installment.common.NotFoundException;
import uz.installment.scoring.CreditLimit;
import uz.installment.scoring.ScoringEngine.RiskCategory;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PartnerService {

    public record Internal(BigDecimal limit, BigDecimal freeMonthly, boolean covers) {
    }

    public record Offers(Internal internal, List<PartnerLimitCalculator.Offer> partners,
                         PartnerLimitCalculator.Recommendation recommendation) {
    }

    private final PartnerRepository partners;
    private final ClientRepository clients;
    private final ClientService clientService;

    @Transactional(readOnly = true)
    public Offers offers(Long clientId, RiskCategory risk, BigDecimal goodsTotal, BigDecimal downPayment,
                         BigDecimal internalAmount, int term) {
        Client c = clients.findById(clientId).orElseThrow(() -> new NotFoundException("Mijoz", clientId));
        BigDecimal financed = goodsTotal.subtract(downPayment).max(BigDecimal.ZERO);
        CreditLimit.Result lim = clientService.limit(clientId, risk);
        BigDecimal need = internalAmount != null ? internalAmount : financed;
        boolean covers = !c.isBlacklisted() && lim.limit().compareTo(need) >= 0;
        List<PartnerLimitCalculator.Offer> offers = partners.findAllByOrderBySortOrderAscIdAsc().stream()
                .filter(Partner::isActive)
                .map(p -> PartnerLimitCalculator.offer(toTerms(p), c.getMonthlyIncome(), risk, goodsTotal, financed,
                        downPayment, term))
                .toList();
        return new Offers(new Internal(lim.limit(), lim.freeMonthly(), covers), offers,
                PartnerLimitCalculator.recommend(covers, offers));
    }

    public static PartnerLimitCalculator.Terms toTerms(Partner p) {
        return new PartnerLimitCalculator.Terms(p.getCode(), p.getName(), p.getCommissionPct(), p.getMaxTerm(),
                p.getMinDownPct(), p.getDtiPct());
    }
}
