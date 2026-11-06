package uz.installment.document;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.installment.document.DocumentTemplateService.Code;
import uz.installment.security.AuthUser;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documents;
    private final DocumentTemplateService templates;

    public record TemplateUpdate(@NotBlank @Size(max = 200) String title, @NotBlank @Size(max = 20000) String body) {
    }

    @GetMapping("/templates")
    @PreAuthorize("hasAnyAuthority('DOCUMENTS_EDIT','CONTRACTS_VIEW','COLLECTION_VIEW')")
    public List<DocumentTemplateService.Template> templates() {
        return templates.list();
    }

    @PutMapping("/templates/{code}/{lang}")
    @PreAuthorize("hasAuthority('DOCUMENTS_EDIT')")
    public DocumentTemplateService.Template save(@PathVariable Code code, @PathVariable String lang,
                                                 @Valid @RequestBody TemplateUpdate req) {
        return templates.save(code, lang(lang), req.title(), req.body());
    }

    @GetMapping("/contracts/{id}/contract")
    @PreAuthorize("hasAuthority('CONTRACTS_VIEW')")
    public ResponseEntity<byte[]> contract(@PathVariable Long id, @RequestParam(defaultValue = "uz") String lang,
                                           @AuthenticationPrincipal AuthUser me) {
        return pdf(documents.contract(id, lang(lang), me));
    }

    @PostMapping("/contracts/{id}/demand")
    @PreAuthorize("hasAuthority('COLLECTION_WORK')")
    public ResponseEntity<byte[]> demand(@PathVariable Long id, @RequestParam(defaultValue = "uz") String lang,
                                         @AuthenticationPrincipal AuthUser me) {
        return pdf(documents.issue(id, Code.DEMAND, lang(lang), me));
    }

    @PostMapping("/contracts/{id}/claim")
    @PreAuthorize("hasAuthority('COLLECTION_LEGAL')")
    public ResponseEntity<byte[]> claim(@PathVariable Long id, @RequestParam(defaultValue = "uz") String lang,
                                        @AuthenticationPrincipal AuthUser me) {
        return pdf(documents.issue(id, Code.CLAIM, lang(lang), me));
    }

    @GetMapping("/contracts/{id}/log")
    @PreAuthorize("hasAnyAuthority('CONTRACTS_VIEW','COLLECTION_VIEW')")
    public List<DocumentService.LogRow> log(@PathVariable Long id, @AuthenticationPrincipal AuthUser me) {
        return documents.log(id, me);
    }

    private static String lang(String l) {
        return "ru".equals(l) ? "ru" : "uz";
    }

    private static ResponseEntity<byte[]> pdf(DocumentService.Pdf pdf) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(pdf.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(pdf.bytes());
    }
}
