package com.kvs.admission.notification;

import com.kvs.admission.enums.EducationBoard;
import com.kvs.admission.enums.TcVerificationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stand-in for the board registries until real integrations exist. Replaced automatically when a real
 * {@link EducationBoardGateway} bean is registered.
 * <p>
 * Expected TC number format: {@code <BOARD>/<school code 4-7 digits>/<year>/<serial 3-6 digits>},
 * e.g. {@code CBSE/2730125/2026/004512}. Deterministic results:
 * <ul>
 *   <li>number not in the format, or prefixed with a different board: INVALID</li>
 *   <li>serial ending in 000: NOT_FOUND (simulates a certificate the board has no record of)</li>
 *   <li>anything else: VALID</li>
 * </ul>
 */
@Slf4j
public class StubEducationBoardGateway implements EducationBoardGateway {

    private static final Pattern TC_NUMBER = Pattern.compile("^([A-Z_]{2,11})/(\\d{4,7})/(\\d{4})/(\\d{3,6})$");

    @Override
    public BoardLookupResult verifyTransferCertificate(EducationBoard board, String tcNumber, String candidateName,
                                                       String previousSchoolName) {
        log.info("[Board registry stub] {} lookup of TC {}", board, tcNumber);
        Matcher m = TC_NUMBER.matcher(tcNumber == null ? "" : tcNumber.trim().toUpperCase());
        if (!m.matches()) {
            return new BoardLookupResult(TcVerificationResult.INVALID, null, null, null,
                    "TC number does not match the " + board + " format BOARD/SCHOOLCODE/YEAR/SERIAL");
        }
        if (!m.group(1).equals(board.name())) {
            return new BoardLookupResult(TcVerificationResult.INVALID, null, null, null,
                    "TC number was issued under " + m.group(1) + ", not " + board);
        }
        if (m.group(4).endsWith("000")) {
            return new BoardLookupResult(TcVerificationResult.NOT_FOUND, null, null, null,
                    "No certificate with this number is registered with " + board);
        }
        int year = Integer.parseInt(m.group(3));
        String reference = board + "-REG-" + m.group(2) + "-" + m.group(4);
        return new BoardLookupResult(TcVerificationResult.VALID, reference,
                previousSchoolName != null ? previousSchoolName : "School " + m.group(2),
                LocalDate.of(year, 4, 15), "Certificate found in the " + board + " registry");
    }

    @Configuration
    static class DefaultBoardGatewayConfig {
        @Bean
        @ConditionalOnMissingBean(EducationBoardGateway.class)
        EducationBoardGateway stubEducationBoardGateway() {
            return new StubEducationBoardGateway();
        }
    }
}
