package uz.installment.warehouse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uz.installment.common.NotFoundException;
import uz.installment.security.AuthUser;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/warehouse")
@RequiredArgsConstructor
public class WarehouseController {

    private final WarehouseService service;
    private final SupplierRepository suppliers;

    public record SupplierDto(Long id, String name, String phone, String contactPerson, int leadTimeDays, String note,
                              boolean active) {
        static SupplierDto of(Supplier s) {
            return new SupplierDto(s.getId(), s.getName(), s.getPhone(), s.getContactPerson(), s.getLeadTimeDays(),
                    s.getNote(), s.isActive());
        }
    }

    public record SupplierRequest(@NotBlank @Size(max = 150) String name, @Size(max = 30) String phone,
                                  @Size(max = 150) String contactPerson, @Min(0) @Max(180) int leadTimeDays,
                                  @Size(max = 500) String note, boolean active) {
    }

    public record ItemInput(@NotNull Long productId, @Min(1) int quantity, @PositiveOrZero BigDecimal unitCost) {
    }

    public record OrderRequest(@NotNull Long supplierId, LocalDate eta, @Size(max = 500) String notes,
                               @NotEmpty @Valid List<ItemInput> items) {
    }

    public record OrderItemDto(Long productId, String productName, String sku, int quantity, BigDecimal unitCost,
                               BigDecimal lineTotal) {
    }

    public record OrderDto(Long id, String orderNo, Long supplierId, String supplierName, String status, LocalDate eta,
                           String notes, String createdBy, String approvedBy, Instant createdAt, Instant approvedAt,
                           Instant receivedAt, int units, BigDecimal total, List<OrderItemDto> items) {
        static OrderDto of(PurchaseOrder o, boolean withItems) {
            List<OrderItemDto> items = withItems ? o.getItems().stream().map(i -> new OrderItemDto(
                    i.getProduct().getId(), i.getProduct().getName(), i.getProduct().getSku(), i.getQuantity(),
                    i.getUnitCost(), i.getUnitCost().multiply(BigDecimal.valueOf(i.getQuantity())))).toList() : List.of();
            int units = withItems ? o.getItems().stream().mapToInt(PurchaseOrderItem::getQuantity).sum() : 0;
            BigDecimal total = items.stream().map(OrderItemDto::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
            return new OrderDto(o.getId(), o.getOrderNo(), o.getSupplier().getId(), o.getSupplier().getName(),
                    o.getStatus().name(), o.getEta(), o.getNotes(),
                    o.getCreatedBy() == null ? null : o.getCreatedBy().getFullName(),
                    o.getApprovedBy() == null ? null : o.getApprovedBy().getFullName(), o.getCreatedAt(),
                    o.getApprovedAt(), o.getReceivedAt(), units, total, items);
        }
    }

    @GetMapping("/analytics")
    @PreAuthorize("hasAuthority('PRODUCTS_VIEW')")
    public WarehouseService.Analytics analytics(@RequestParam(defaultValue = "30") int cover) {
        return service.analytics(cover);
    }

    // ---- yetkazib beruvchilar

    @GetMapping("/suppliers")
    @PreAuthorize("hasAuthority('PRODUCTS_VIEW')")
    @Transactional(readOnly = true)
    public List<SupplierDto> suppliers() {
        return suppliers.findAllByOrderByNameAsc().stream().map(SupplierDto::of).toList();
    }

    @PostMapping("/suppliers")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PRODUCTS_EDIT')")
    @Transactional
    public SupplierDto createSupplier(@Valid @RequestBody SupplierRequest r) {
        Supplier s = new Supplier();
        apply(s, r);
        return SupplierDto.of(suppliers.save(s));
    }

    @PutMapping("/suppliers/{id}")
    @PreAuthorize("hasAuthority('PRODUCTS_EDIT')")
    @Transactional
    public SupplierDto updateSupplier(@PathVariable Long id, @Valid @RequestBody SupplierRequest r) {
        Supplier s = suppliers.findById(id).orElseThrow(() -> new NotFoundException("Yetkazib beruvchi", id));
        apply(s, r);
        return SupplierDto.of(s);
    }

    private static void apply(Supplier s, SupplierRequest r) {
        s.setName(r.name().trim());
        s.setPhone(r.phone());
        s.setContactPerson(r.contactPerson());
        s.setLeadTimeDays(r.leadTimeDays());
        s.setNote(r.note());
        s.setActive(r.active());
    }

    // ---- xarid buyurtmalari

    @GetMapping("/orders")
    @PreAuthorize("hasAuthority('PRODUCTS_VIEW')")
    @Transactional(readOnly = true)
    public List<OrderDto> orders() {
        return service.list().stream().map(o -> OrderDto.of(o, false)).toList();
    }

    @GetMapping("/orders/{id}")
    @PreAuthorize("hasAuthority('PRODUCTS_VIEW')")
    @Transactional(readOnly = true)
    public OrderDto order(@PathVariable Long id) {
        return OrderDto.of(service.get(id), true);
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PRODUCTS_EDIT')")
    @Transactional
    public OrderDto createOrder(@Valid @RequestBody OrderRequest r, @AuthenticationPrincipal AuthUser me) {
        var o = service.create(r.supplierId(), r.eta(), r.notes(),
                r.items().stream().map(i -> new WarehouseService.ItemRequest(i.productId(), i.quantity(), i.unitCost())).toList(), me);
        return OrderDto.of(service.get(o.getId()), true);
    }

    @PostMapping("/orders/{id}/approve")
    @PreAuthorize("hasAuthority('PRODUCTS_EDIT')")
    @Transactional
    public OrderDto approve(@PathVariable Long id, @AuthenticationPrincipal AuthUser me) {
        return OrderDto.of(service.approve(id, me), true);
    }

    @PostMapping("/orders/{id}/receive")
    @PreAuthorize("hasAuthority('PRODUCTS_EDIT')")
    @Transactional
    public OrderDto receive(@PathVariable Long id) {
        return OrderDto.of(service.receive(id), true);
    }

    @PostMapping("/orders/{id}/cancel")
    @PreAuthorize("hasAuthority('PRODUCTS_EDIT')")
    @Transactional
    public OrderDto cancel(@PathVariable Long id) {
        return OrderDto.of(service.cancel(id), true);
    }
}
