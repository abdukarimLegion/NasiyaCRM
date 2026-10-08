package uz.installment.client;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uz.installment.client.ClientDtos.ClientDetails;
import uz.installment.client.ClientDtos.ClientRequest;
import uz.installment.client.ClientDtos.ClientResponse;
import uz.installment.common.PageResponse;

@RestController
@RequestMapping("/api/clients")
@RequiredArgsConstructor
public class ClientController {

    private final ClientService service;

    @GetMapping
    public PageResponse<ClientDtos.ClientListItem> list(@RequestParam(required = false) String q,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        return service.search(q, page, size);
    }

    @GetMapping("/summary")
    public ClientDtos.ClientsSummary summary() {
        return service.summary();
    }

    @GetMapping("/{id}")
    public ClientDetails get(@PathVariable Long id) {
        return service.details(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','CREDIT_OFFICER')")
    public ClientResponse create(@Valid @RequestBody ClientRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','CREDIT_OFFICER')")
    public ClientResponse update(@PathVariable Long id, @Valid @RequestBody ClientRequest req) {
        return service.update(id, req);
    }
}
