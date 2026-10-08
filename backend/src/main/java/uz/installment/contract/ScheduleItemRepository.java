package uz.installment.contract;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface ScheduleItemRepository extends JpaRepository<ScheduleItem, Long> {

    /** Shartnoma bo'yicha jadval yig'indisi: to'langan, qolgan va navbatdagi to'lov sanasi. */
    interface ScheduleTotals {
        Long getContractId();

        BigDecimal getPaid();

        BigDecimal getRemaining();

        LocalDate getNextDue();
    }

    @Query(value = """
            select contract_id as "contractId", sum(paid_amount) as "paid",
                   sum(amount - paid_amount) as "remaining",
                   min(due_date) filter (where status <> 'PAID') as "nextDue"
            from schedule_items
            where contract_id in (:ids)
            group by contract_id
            """, nativeQuery = true)
    List<ScheduleTotals> totalsFor(@Param("ids") Collection<Long> ids);

    @Modifying
    @Transactional
    @Query(value = """
            update schedule_items set status = 'LATE'
            where status in ('PENDING','PARTIAL') and due_date < :today
            """, nativeQuery = true)
    int markLateItems(@Param("today") LocalDate today);

    /** Eslatmalar uchun: shu sanada to'lanishi kerak bo'lgan va hali yopilmagan qismlar. */
    @Query("""
            select s from ScheduleItem s
            join fetch s.contract c
            join fetch c.client
            where s.dueDate = :due
              and s.status <> uz.installment.contract.ScheduleStatus.PAID
              and c.status in (uz.installment.contract.ContractStatus.ACTIVE, uz.installment.contract.ContractStatus.LATE)
            """)
    List<ScheduleItem> findOpenDueOn(@Param("due") LocalDate due);
}
