package uz.installment.document;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

/**
 * Oddiy, chop etishga mos A4 hujjat: sarlavha bloki, matn (bo'sh qator — yangi xatboshi),
 * ixtiyoriy jadval va imzo joylari. Kirill va o'zbek lotin harflari uchun DejaVu Sans ishlatiladi.
 */
public final class PdfRenderer {

    public record Table(List<String> headers, List<List<String>> rows, float[] widths, int[] rightAligned) {
    }

    /** headerLines — o'ng yuqori burchakdagi qatorlar (kompaniya, sana, raqam). */
    public record Doc(String title, List<String> headerLines, String body, Table table, List<String> signatures,
                      String footnote) {
    }

    private static final Color INK = new Color(30, 41, 59);
    private static final Color MUTED = new Color(100, 116, 139);
    private static final Color LINE = new Color(203, 213, 225);
    private static final Color HEAD_BG = new Color(241, 245, 249);

    private static final BaseFont REGULAR = font("/fonts/DejaVuSans.ttf");
    private static final BaseFont BOLD = font("/fonts/DejaVuSans-Bold.ttf");

    private PdfRenderer() {
    }

    public static String fill(String template, Map<String, String> vars) {
        String out = template;
        for (var e : vars.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
        }
        return out;
    }

    public static byte[] render(Doc d) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 56, 48, 48, 48);
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();
            Font small = new Font(REGULAR, 9, Font.NORMAL, MUTED);
            Font body = new Font(REGULAR, 10.5f, Font.NORMAL, INK);
            Font bold = new Font(BOLD, 10.5f, Font.NORMAL, INK);
            Font title = new Font(BOLD, 13, Font.NORMAL, INK);

            for (String line : d.headerLines()) {
                Paragraph p = new Paragraph(line, small);
                p.setAlignment(Element.ALIGN_RIGHT);
                doc.add(p);
            }
            doc.add(new Chunk(new LineSeparator(0.6f, 100, LINE, Element.ALIGN_CENTER, -6)));

            Paragraph t = new Paragraph(d.title(), title);
            t.setAlignment(Element.ALIGN_CENTER);
            t.setSpacingBefore(18);
            t.setSpacingAfter(14);
            doc.add(t);

            for (String para : d.body().split("\\r?\\n\\s*\\r?\\n")) {
                Paragraph p = new Paragraph();
                p.setLeading(15.5f);
                p.setSpacingAfter(8);
                p.setAlignment(Element.ALIGN_JUSTIFIED);
                String[] lines = para.strip().split("\\r?\\n");
                for (int i = 0; i < lines.length; i++) {
                    String line = lines[i];
                    int colon = line.indexOf(':');
                    // "Da'vogar: ..." kabi qatorlarning yorlig'i qalin
                    if (lines.length > 1 && colon > 0 && colon < 24) {
                        p.add(new Phrase(line.substring(0, colon + 1), bold));
                        p.add(new Phrase(line.substring(colon + 1), body));
                    } else {
                        p.add(new Phrase(line, body));
                    }
                    if (i < lines.length - 1) p.add(Chunk.NEWLINE);
                }
                doc.add(p);
            }

            if (d.table() != null) {
                doc.add(table(d.table()));
            }

            if (d.signatures() != null && !d.signatures().isEmpty()) {
                PdfPTable sig = new PdfPTable(d.signatures().size());
                sig.setWidthPercentage(100);
                sig.setSpacingBefore(30);
                for (String s : d.signatures()) {
                    PdfPCell c = new PdfPCell();
                    c.setBorder(Rectangle.NO_BORDER);
                    c.setPaddingRight(16);
                    String[] parts = s.split("\n");
                    c.addElement(new Paragraph(parts[0], bold));
                    for (int i = 1; i < parts.length; i++) {
                        c.addElement(new Paragraph(parts[i], small));
                    }
                    Paragraph line = new Paragraph("\n______________________________", body);
                    c.addElement(line);
                    c.addElement(new Paragraph("(imzo / подпись)", small));
                    sig.addCell(c);
                }
                doc.add(sig);
            }
            if (d.footnote() != null) {
                Paragraph f = new Paragraph(d.footnote(), small);
                f.setSpacingBefore(18);
                doc.add(f);
            }
            doc.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("PDF yaratilmadi", e);
        }
        return out.toByteArray();
    }

    private static PdfPTable table(Table t) throws DocumentException {
        Font head = new Font(BOLD, 8.5f, Font.NORMAL, INK);
        Font cell = new Font(REGULAR, 8.5f, Font.NORMAL, INK);
        PdfPTable table = new PdfPTable(t.headers().size());
        table.setWidthPercentage(100);
        table.setWidths(t.widths());
        table.setSpacingBefore(6);
        table.setHeaderRows(1);
        for (int i = 0; i < t.headers().size(); i++) {
            PdfPCell c = new PdfPCell(new Phrase(t.headers().get(i), head));
            c.setBackgroundColor(HEAD_BG);
            c.setBorderColor(LINE);
            c.setPadding(5);
            c.setHorizontalAlignment(isRight(t, i) ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
            table.addCell(c);
        }
        for (List<String> row : t.rows()) {
            for (int i = 0; i < row.size(); i++) {
                PdfPCell c = new PdfPCell(new Phrase(row.get(i), cell));
                c.setBorderColor(LINE);
                c.setPadding(4.5f);
                c.setHorizontalAlignment(isRight(t, i) ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
                table.addCell(c);
            }
        }
        return table;
    }

    private static boolean isRight(Table t, int col) {
        for (int r : t.rightAligned()) {
            if (r == col) return true;
        }
        return false;
    }

    private static BaseFont font(String path) {
        try (InputStream in = PdfRenderer.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Shrift topilmadi: " + path);
            }
            byte[] bytes = in.readAllBytes();
            return BaseFont.createFont(path.substring(path.lastIndexOf('/') + 1), BaseFont.IDENTITY_H,
                    BaseFont.EMBEDDED, true, bytes, null);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
