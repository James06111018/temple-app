package tw.org.il.dongsheng.templeapp.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record MeritBoxOpening(
        Long id,
        LocalDate openingDate,
        String serialNo,
        long amount,
        String opener,
        String note,
        String categoryCode,
        String categoryName,
        String createdBy,
        LocalDateTime createdAt
) {

}
