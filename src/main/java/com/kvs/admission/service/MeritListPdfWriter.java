package com.kvs.admission.service;

import com.kvs.admission.dto.MeritListEntryResponse;
import com.kvs.admission.dto.MeritListResponse;
import com.kvs.core.pdf.PdfDocument;
import com.kvs.core.pdf.PdfDocument.Font;
import com.kvs.core.pdf.SchoolLetterhead;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import static com.kvs.core.pdf.SchoolLetterhead.*;

/**
 * The merit list (US-02012) as an A4 PDF on the school letterhead from the school profile (roadmap C2): the list's grade,
 * year, branch and weights, then the ranked candidates. Names are masked ("Aarav S.") unless full names are asked for.
 * Long lists continue on further pages with the letterhead repeated. Provisional, so no verification code.
 */
@Component
public class MeritListPdfWriter {

    static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a 'IST'", Locale.ENGLISH);

    /** Column x positions and the row height */
    private static final float COL_RANK = LEFT + 10, COL_APP = LEFT + 50, COL_NAME = LEFT + 175, COL_SCORE = LEFT + 355,
            COL_STATUS = LEFT + 430;
    private static final float ROW = 20;
    /** Lowest y a row may reach before the table continues on the next page (above the footer) */
    private static final float TABLE_LIMIT = PdfDocument.HEIGHT - 70;

    public byte[] write(SchoolLetterhead school, MeritListResponse list, boolean fullNames) {
        return write(school, list, fullNames, Instant.now());
    }

    byte[] write(SchoolLetterhead school, MeritListResponse list, boolean fullNames, Instant generatedAt) {
        PdfDocument pdf = new PdfDocument();
        String code = nz(list.getMeritListCode());
        float y = school.draw(pdf, "MERIT LIST", "List " + code);

        pdf.text(Font.BOLD, 13, BRAND, LEFT, y + 12, PdfDocument.fit(Font.BOLD, 13,
                "Provisional merit list: grade " + display(list.getTargetGradeId()) + ", " + display(list.getAcademicYearId()), RIGHT - LEFT));
        pdf.text(Font.REGULAR, 9, MUTED, LEFT, y + 28, PdfDocument.fit(Font.REGULAR, 9,
                "Branch " + nz(list.getBranchId()) + "   |   Weights: entrance test " + percent(list.getEntranceTestWeight())
                        + ", interview " + percent(list.getInterviewWeight()), RIGHT - LEFT));
        y += 44;

        y = tableHeader(pdf, y);
        List<MeritListEntryResponse> entries = list.getEntries() == null ? List.of() : list.getEntries();
        if (entries.isEmpty()) {
            pdf.text(Font.REGULAR, 10, MUTED, LEFT + 10, y + 16, "No candidates on this list yet.");
            y += ROW + 20;
        }
        int i = 0;
        for (MeritListEntryResponse e : entries) {
            if (y + ROW > TABLE_LIMIT) {
                pdf.newPage();
                y = tableHeader(pdf, school.draw(pdf, "MERIT LIST", "List " + code + " (continued)"));
            }
            if (i % 2 == 1) pdf.fill(ZEBRA, LEFT, y, RIGHT - LEFT, ROW);
            float base = y + 13.5f;
            pdf.text(Font.BOLD, 9, INK, COL_RANK, base, String.valueOf(e.getMeritRank()));
            pdf.text(Font.REGULAR, 9, INK, COL_APP, base, PdfDocument.fit(Font.REGULAR, 9, nz(e.getApplicationNumber()), COL_NAME - COL_APP - 10));
            pdf.text(Font.REGULAR, 9, INK, COL_NAME, base, PdfDocument.fit(Font.REGULAR, 9,
                    fullNames ? nz(e.getCandidateName()) : maskName(e.getCandidateName()), COL_SCORE - COL_NAME - 10));
            pdf.textRight(Font.REGULAR, 9, INK, COL_STATUS - 16, base, e.getWeightedScore() == null ? "-" : e.getWeightedScore().toPlainString());
            pdf.text(Font.REGULAR, 9, e.isQualified() ? GREEN : MUTED, COL_STATUS, base, e.isQualified() ? "Qualified" : "Under review");
            y += ROW;
            i++;
        }
        pdf.line(INK, 0.8f, LEFT, y, RIGHT, y);

        String note = "Provisional and subject to document verification. Equal scores share a rank.  |  Generated "
                + STAMP.format(generatedAt.atZone(ZONE));
        for (int p = 1; p <= pdf.pageCount(); p++) {
            pdf.onPage(p);
            school.footer(pdf, note, p, pdf.pageCount());
        }
        return pdf.toBytes();
    }

    /** The header row of the table; returns the y below it. */
    private static float tableHeader(PdfDocument pdf, float y) {
        pdf.fill(BRAND, LEFT, y, RIGHT - LEFT, ROW);
        float base = y + 13.5f;
        pdf.text(Font.BOLD, 8.5f, WHITE, COL_RANK, base, "Rank");
        pdf.text(Font.BOLD, 8.5f, WHITE, COL_APP, base, "Application no.");
        pdf.text(Font.BOLD, 8.5f, WHITE, COL_NAME, base, "Candidate");
        pdf.textRight(Font.BOLD, 8.5f, WHITE, COL_STATUS - 16, base, "Weighted score");
        pdf.text(Font.BOLD, 8.5f, WHITE, COL_STATUS, base, "Status");
        return y + ROW;
    }

    /** "Aarav Sharma" -> "Aarav S."; single names are kept. */
    static String maskName(String name) {
        if (name == null || name.isBlank()) return "";
        String[] parts = name.trim().split("\s+");
        if (parts.length == 1) return parts[0];
        return parts[0] + " " + parts[parts.length - 1].charAt(0) + ".";
    }

    private static String display(String id) {
        return id == null || id.isBlank() ? "-" : id.replace("GRADE-", "").replace('_', ' ');
    }

    private static String percent(java.math.BigDecimal weight) {
        return weight == null ? "-" : weight.multiply(java.math.BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString() + "%";
    }

    private static String nz(String s) {
        return s == null || s.isBlank() ? "-" : s;
    }
}
