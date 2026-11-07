package uz.installment.mib;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** MIB sozlanmagan: avtomatik tekshiruv yo'q, natijani xodim qo'lda kiritadi. */
@Component
@ConditionalOnProperty(name = "app.mib.provider", havingValue = "none", matchIfMissing = true)
public class NoopMibClient implements MibClient {

    @Override
    public MibOutcome check(String pinfl) {
        return MibOutcome.error("MIB xizmati sozlanmagan (APP_MIB_PROVIDER). Natijani qo'lda kiriting.");
    }

    @Override
    public String name() {
        return "none";
    }

    @Override
    public boolean isConfigured() {
        return false;
    }
}
