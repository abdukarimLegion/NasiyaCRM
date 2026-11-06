package uz.installment.document;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PdfRendererTest {

    @Test
    void fillReplacesPlaceholders() {
        assertThat(PdfRenderer.fill("{client}: {remaining} ({missing})", Map.of("client", "Ali", "remaining", "100")))
                .isEqualTo("Ali: 100 ({missing})");
    }

    @Test
    void rendersCyrillicAndUzbekLatinWithTable() {
        var doc = new PdfRenderer.Doc("ТРЕБОВАНИЕ № T-2026/00001",
                List.of("Kompaniya", "Chiqish № T-2026/00001"),
                "Hurmatli G'ulomjon O'ktamovich!\n\nУважаемый клиент, просим оплатить.\nDa'vogar: MChJ",
                new PdfRenderer.Table(List.of("№", "Sana", "To'lov"), List.of(List.of("1", "01.01.2026", "100 so'm")),
                        new float[]{1, 2, 2}, new int[]{2}),
                List.of("Sotuvchi\nMChJ", "Xaridor\nAli"), "Jarima hisoblanmaydi.");
        byte[] pdf = PdfRenderer.render(doc);
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(pdf.length).isGreaterThan(5_000);
    }
}
