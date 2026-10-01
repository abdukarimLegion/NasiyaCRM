package uz.installment.product;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ProductController {

    private final ProductRepository products;
    private final CategoryRepository categories;

    public record CategoryDto(Long id, String code, String nameUz, String nameRu) {
        static CategoryDto of(Category c) {
            return new CategoryDto(c.getId(), c.getCode(), c.getNameUz(), c.getNameRu());
        }
    }

    public record ProductRequest(
            @NotNull Long categoryId,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 50) String sku,
            @NotNull @Positive BigDecimal price,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal markupPct,
            @Min(1) @Max(60) int termMin,
            @Min(1) @Max(60) int termMax,
            @PositiveOrZero int stock,
            boolean active) {
    }

    public record ProductResponse(Long id, Long categoryId, String categoryCode, String name, String sku,
                                  BigDecimal price, BigDecimal markupPct, int termMin, int termMax, int stock,
                                  boolean active) {
        static ProductResponse of(Product p) {
            return new ProductResponse(p.getId(), p.getCategory().getId(), p.getCategory().getCode(), p.getName(),
                    p.getSku(), p.getPrice(), p.getMarkupPct(), p.getTermMin(), p.getTermMax(), p.getStock(),
                    p.isActive());
        }
    }

    @GetMapping("/categories")
    public List<CategoryDto> categories() {
        return categories.findAllByOrderByIdAsc().stream().map(CategoryDto::of).toList();
    }

    @GetMapping("/products")
    @Transactional(readOnly = true)
    public List<ProductResponse> list(@RequestParam(required = false) Long categoryId,
                                      @RequestParam(defaultValue = "false") boolean activeOnly) {
        return products.findFiltered(categoryId, activeOnly).stream().map(ProductResponse::of).toList();
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ProductResponse create(@Valid @RequestBody ProductRequest req) {
        Product p = new Product();
        apply(p, req);
        return ProductResponse.of(products.save(p));
    }

    @PutMapping("/products/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ProductResponse update(@PathVariable Long id, @Valid @RequestBody ProductRequest req) {
        Product p = products.findById(id).orElseThrow(() -> new NotFoundException("Mahsulot", id));
        apply(p, req);
        return ProductResponse.of(p);
    }

    private void apply(Product p, ProductRequest r) {
        if (r.termMax() < r.termMin()) {
            throw new IllegalArgumentException("termMax termMin dan kichik bo'lmasligi kerak");
        }
        p.setCategory(categories.findById(r.categoryId())
                .orElseThrow(() -> new NotFoundException("Kategoriya", r.categoryId())));
        p.setName(r.name().trim());
        p.setSku(r.sku());
        p.setPrice(r.price());
        p.setMarkupPct(r.markupPct());
        p.setTermMin(r.termMin());
        p.setTermMax(r.termMax());
        p.setStock(r.stock());
        p.setActive(r.active());
    }
}
