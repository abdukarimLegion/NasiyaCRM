package uz.installment.partner.gateway;

import org.springframework.stereotype.Component;

import java.util.List;

/** Hamkor kodi bo'yicha gateway tanlaydi: haqiqiy integratsiya bo'lsa — o'sha, bo'lmasa mock. */
@Component
public class PartnerGateways {

    private final List<PartnerGateway> real;
    private final MockPartnerGateway mock;

    public PartnerGateways(List<PartnerGateway> all, MockPartnerGateway mock) {
        this.real = all.stream().filter(g -> !g.isMock()).toList();
        this.mock = mock;
    }

    public PartnerGateway forCode(String partnerCode) {
        return real.stream().filter(g -> g.supports(partnerCode)).findFirst().orElse(mock);
    }
}
