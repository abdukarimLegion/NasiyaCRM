package uz.installment.warehouse;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.installment.common.BusinessException;
import uz.installment.common.NotFoundException;
import uz.installment.product.Product;
import uz.installment.product.ProductRepository;
import uz.installment.security.AuthUser;
import uz.installment.security.UserRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ombor tahlili: sotuv tezligi, zaxira kunlari, "o'lik" tovar va buyurtma tavsiyalari; xarid buyurtmalari.
 * Sotuv = shartnomalardagi mahsulot miqdori + hamkorga topshirilgan savdolar (bekor qilinganlari hisobga olinmaydi).
 */
@Service
@RequiredArgsConstructor
public class WarehouseService {

    /** OUT — qoldiq 0; CRITICAL — yetkazib berish muddatiga yetmaydi; LOW — qoplash kunlari yetarli emas; OK; DEAD — 90 kunda sotilmagan; OVER — haddan ortiq zaxira */
    public enum StockStatus { OUT, CRITICAL, LOW, OK, OVER, DEAD }

    public record ProductRow(Long id, String name, String sku, String category, Long supplierId, String supplierName,
                             int stock, BigDecimal price, BigDecimal stockValue, int sold30, int sold90,
                             double perDay, Double coverDays, int leadDays, int incoming, int recommendedQty,
                             BigDecimal recommendedCost, StockStatus status) {
    }

    public record Summary(int products, int units, BigDecimal stockValue, int out, int critical, int low, int dead,
                          BigDecimal deadValue, BigDecimal turnoverPerYear, int toOrderUnits, BigDecimal toOrderCost) {
    }

    public record SupplierSuggestion(Long supplierId, String supplierName, int leadDays, int lines, int units,
                                     BigDecimal cost) {
    }

    public record Analytics(int coverDays, Summary summary, List<ProductRow> products,
                            List<SupplierSuggestion> suggestions) {
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final SupplierRepository suppliers;
    private final PurchaseOrderRepository orders;
    private final ProductRepository products;
    private final UserRepository users;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Analytics analytics(int coverDays) {
        int cover = Math.max(7, Math.min(coverDays, 180));
        Instant now = Instant.now(clock);
        var p = new MapSqlParameterSource()
                .addValue("d30", java.sql.Timestamp.from(now.minusSeconds(30L * 86400)))
                .addValue("d90", java.sql.Timestamp.from(now.minusSeconds(90L * 86400)));
        // Sotuv: shartnoma qatorlari (bekor qilinmagan) + hamkorga topshirilgan arizalar (savat JSON'idan)
        List<Object[]> rows = new ArrayList<>();
        jdbc.query("""
                with sold as (
                  select ci.product_id as pid, ci.quantity as qty, c.created_at as ts
                  from contract_items ci join contracts c on c.id = ci.contract_id
                  where c.status <> 'CANCELLED' and ci.product_id is not null
                  union all
                  select c.product_id, 1, c.created_at from contracts c
                  where c.status <> 'CANCELLED' and c.product_id is not null
                    and not exists (select 1 from contract_items x where x.contract_id = c.id)
                  union all
                  select (e->>'productId')::bigint, coalesce((e->>'quantity')::int, 1), a.partner_handover_at
                  from applications a,
                       jsonb_array_elements(coalesce(a.request->'terms'->'items',
                         jsonb_build_array(jsonb_build_object('productId', a.request->'terms'->'productId', 'quantity', 1)))) e
                  where a.status = 'PARTNER' and (e->>'productId') is not null
                ),
                incoming as (
                  select i.product_id as pid, sum(i.quantity) as qty
                  from purchase_order_items i join purchase_orders o on o.id = i.order_id
                  where o.status in ('DRAFT','APPROVED') group by i.product_id
                )
                select p.id, p.name, p.sku, cat.name_uz as category, p.supplier_id, s.name as supplier_name,
                       coalesce(s.lead_time_days, 7) as lead, p.stock, p.price,
                       coalesce(sum(sold.qty) filter (where sold.ts >= :d30), 0) as s30,
                       coalesce(sum(sold.qty) filter (where sold.ts >= :d90), 0) as s90,
                       coalesce(max(inc.qty), 0) as incoming
                from products p
                join categories cat on cat.id = p.category_id
                left join suppliers s on s.id = p.supplier_id
                left join sold on sold.pid = p.id
                left join incoming inc on inc.pid = p.id
                where p.active
                group by p.id, p.name, p.sku, cat.name_uz, p.supplier_id, s.name, s.lead_time_days, p.stock, p.price
                order by p.name
                """, p, rs -> {
            rows.add(new Object[]{rs.getLong("id"), rs.getString("name"), rs.getString("sku"), rs.getString("category"),
                    rs.getObject("supplier_id", Long.class), rs.getString("supplier_name"), rs.getInt("lead"),
                    rs.getInt("stock"), rs.getBigDecimal("price"), rs.getInt("s30"), rs.getInt("s90"),
                    rs.getInt("incoming")});
        });

        List<ProductRow> out = new ArrayList<>();
        int units = 0, outCnt = 0, crit = 0, low = 0, dead = 0, toOrderUnits = 0;
        BigDecimal stockValue = BigDecimal.ZERO, deadValue = BigDecimal.ZERO, toOrderCost = BigDecimal.ZERO,
                sold90Value = BigDecimal.ZERO;
        Map<Long, int[]> bySupplierUnits = new HashMap<>();
        Map<Long, BigDecimal> bySupplierCost = new HashMap<>();
        Map<Long, String> supplierNames = new HashMap<>();
        Map<Long, Integer> supplierLead = new HashMap<>();
        for (Object[] r : rows) {
            int stock = (Integer) r[7];
            BigDecimal price = (BigDecimal) r[8];
            int s30 = (Integer) r[9], s90 = (Integer) r[10], incoming = (Integer) r[11], lead = (Integer) r[6];
            // Tezlik: oxirgi 30 kun (60%) va 90 kun (40%) aralashmasi — mavsumiy o'zgarishga tezroq javob beradi
            double perDay = 0.6 * s30 / 30.0 + 0.4 * s90 / 90.0;
            Double coverD = perDay > 0 ? stock / perDay : null;
            int target = (int) Math.ceil(perDay * (lead + cover));
            int rec = Math.max(0, target - stock - incoming);
            StockStatus st;
            if (stock <= 0) {
                st = StockStatus.OUT;
            } else if (s90 == 0) {
                st = StockStatus.DEAD;
            } else if (coverD != null && coverD < lead) {
                st = StockStatus.CRITICAL;
            } else if (coverD != null && coverD < lead + cover / 2.0) {
                st = StockStatus.LOW;
            } else if (coverD != null && coverD > 6.0 * cover) {
                st = StockStatus.OVER;
            } else {
                st = StockStatus.OK;
            }
            // Qoldiq 0, lekin sotuv bo'lgan tovar ham buyurtmaga muhtoj (OUT + rec > 0)
            BigDecimal value = price.multiply(BigDecimal.valueOf(stock));
            BigDecimal recCost = price.multiply(BigDecimal.valueOf(rec));
            units += stock;
            stockValue = stockValue.add(value);
            sold90Value = sold90Value.add(price.multiply(BigDecimal.valueOf(s90)));
            switch (st) {
                case OUT -> outCnt++;
                case CRITICAL -> crit++;
                case LOW -> low++;
                case DEAD -> {
                    dead++;
                    deadValue = deadValue.add(value);
                }
                default -> {
                }
            }
            if (rec > 0) {
                toOrderUnits += rec;
                toOrderCost = toOrderCost.add(recCost);
                Long sid = (Long) r[4] == null ? 0L : (Long) r[4];
                bySupplierUnits.computeIfAbsent(sid, k -> new int[2]);
                bySupplierUnits.get(sid)[0]++;
                bySupplierUnits.get(sid)[1] += rec;
                bySupplierCost.merge(sid, recCost, BigDecimal::add);
                supplierNames.put(sid, (String) r[5]);
                supplierLead.put(sid, lead);
            }
            out.add(new ProductRow((Long) r[0], (String) r[1], (String) r[2], (String) r[3], (Long) r[4], (String) r[5],
                    stock, price, value, s30, s90, round(perDay, 2), coverD == null ? null : round(coverD, 0),
                    lead, incoming, rec, recCost, st));
        }
        // Yillik aylanish: 90 kunlik sotuv tannarxi × 4 / joriy ombor qiymati
        BigDecimal turnover = stockValue.signum() > 0
                ? sold90Value.multiply(BigDecimal.valueOf(4)).divide(stockValue, 1, java.math.RoundingMode.HALF_UP)
                : null;
        List<SupplierSuggestion> sug = new ArrayList<>();
        bySupplierUnits.forEach((sid, v) -> sug.add(new SupplierSuggestion(sid == 0 ? null : sid,
                sid == 0 ? null : supplierNames.get(sid), supplierLead.getOrDefault(sid, 7), v[0], v[1],
                bySupplierCost.get(sid))));
        sug.sort((a, b) -> b.cost().compareTo(a.cost()));
        return new Analytics(cover, new Summary(rows.size(), units, stockValue, outCnt, crit, low, dead, deadValue,
                turnover, toOrderUnits, toOrderCost), out, sug);
    }

    // ------------------------------------------------------------------ xarid buyurtmalari

    public record ItemRequest(Long productId, int quantity, BigDecimal unitCost) {
    }

    @Transactional
    public PurchaseOrder create(Long supplierId, LocalDate eta, String notes, List<ItemRequest> items, AuthUser me) {
        Supplier s = suppliers.findById(supplierId).orElseThrow(() -> new NotFoundException("Yetkazib beruvchi", supplierId));
        if (items == null || items.isEmpty()) {
            throw new BusinessException("ORDER_EMPTY", "Buyurtmada kamida bitta mahsulot bo'lishi kerak");
        }
        PurchaseOrder o = new PurchaseOrder();
        o.setOrderNo(String.format("XB-%d-%04d", LocalDate.now(clock).getYear(), orders.nextNo()));
        o.setSupplier(s);
        o.setEta(eta != null ? eta : LocalDate.now(clock).plusDays(s.getLeadTimeDays()));
        o.setNotes(notes);
        o.setCreatedBy(users.getReferenceById(me.id()));
        for (ItemRequest it : items) {
            Product pr = products.findById(it.productId()).orElseThrow(() -> new NotFoundException("Mahsulot", it.productId()));
            if (it.quantity() < 1) {
                throw new BusinessException("ORDER_QTY", "Miqdor 1 dan kam bo'lmasligi kerak: " + pr.getName());
            }
            PurchaseOrderItem i = new PurchaseOrderItem();
            i.setProduct(pr);
            i.setQuantity(it.quantity());
            i.setUnitCost(it.unitCost() != null ? it.unitCost() : pr.getPrice());
            o.addItem(i);
        }
        return orders.save(o);
    }

    @Transactional
    public PurchaseOrder approve(Long id, AuthUser me) {
        PurchaseOrder o = orders.findDetailed(id).orElseThrow(() -> new NotFoundException("Buyurtma", id));
        if (o.getStatus() != PurchaseOrder.Status.DRAFT) {
            throw new BusinessException("ORDER_STATE", "Faqat qoralama buyurtmani tasdiqlash mumkin");
        }
        o.setStatus(PurchaseOrder.Status.APPROVED);
        o.setApprovedBy(users.getReferenceById(me.id()));
        o.setApprovedAt(Instant.now());
        return o;
    }

    /** Qabul qilish: tovar ombor qoldig'iga qo'shiladi. */
    @Transactional
    public PurchaseOrder receive(Long id) {
        PurchaseOrder o = orders.findDetailed(id).orElseThrow(() -> new NotFoundException("Buyurtma", id));
        if (o.getStatus() != PurchaseOrder.Status.APPROVED) {
            throw new BusinessException("ORDER_STATE", "Faqat tasdiqlangan buyurtmani qabul qilish mumkin");
        }
        for (PurchaseOrderItem i : o.getItems()) {
            Product pr = products.findForUpdate(i.getProduct().getId()).orElseThrow();
            pr.setStock(pr.getStock() + i.getQuantity());
        }
        o.setStatus(PurchaseOrder.Status.RECEIVED);
        o.setReceivedAt(Instant.now());
        return o;
    }

    @Transactional
    public PurchaseOrder cancel(Long id) {
        PurchaseOrder o = orders.findDetailed(id).orElseThrow(() -> new NotFoundException("Buyurtma", id));
        if (o.getStatus() == PurchaseOrder.Status.RECEIVED) {
            throw new BusinessException("ORDER_STATE", "Qabul qilingan buyurtmani bekor qilib bo'lmaydi");
        }
        o.setStatus(PurchaseOrder.Status.CANCELLED);
        return o;
    }

    @Transactional(readOnly = true)
    public PurchaseOrder get(Long id) {
        return orders.findDetailed(id).orElseThrow(() -> new NotFoundException("Buyurtma", id));
    }

    @Transactional(readOnly = true)
    public List<PurchaseOrder> list() {
        return orders.findAllByOrderByIdDesc();
    }

    private static double round(double v, int scale) {
        double m = Math.pow(10, scale);
        return Math.round(v * m) / m;
    }
}
