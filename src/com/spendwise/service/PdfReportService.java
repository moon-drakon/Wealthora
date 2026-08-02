package com.spendwise.service;

import com.spendwise.model.Account;
import com.spendwise.model.Category;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Creates a compact, dependency-free PDF summary from repository-backed data. */
public final class PdfReportService {
    private static final int MAX_DETAIL_LINES = 42;

    public ExportResult export(
            Path destination,
            boolean allowOverwrite,
            PortfolioAnalyticsSnapshot snapshot,
            String currencyCode) {
        Objects.requireNonNull(snapshot, "Portfolio snapshot is required.");
        String currency = requireText(currencyCode, "Currency code");
        List<String> lines = reportLines(snapshot, currency);
        byte[] pdf = createPdf(lines);
        Path target = Objects.requireNonNull(destination,
                "PDF destination is required.").toAbsolutePath().normalize();
        SafeFileSupport.write(target, pdf, allowOverwrite,
                ".spendwise-pdf-", "PDF report");
        return new ExportResult(target, lines.size());
    }

    private static List<String> reportLines(
            PortfolioAnalyticsSnapshot snapshot, String currency) {
        AdvancedReportSnapshot report = snapshot.transactionReport();
        List<String> lines = new ArrayList<>();
        lines.add("SpendWise Personal Finance Report");
        lines.add("Period: " + report.getStartDate() + " through "
                + report.getEndDate());
        lines.add("");
        lines.add("Income: " + money(report.getTotalIncome(), currency));
        lines.add("Expenses: " + money(report.getTotalExpenses(), currency));
        lines.add("Net cash flow: " + money(report.getNetCashFlow(), currency));
        lines.add("Account total: " + money(snapshot.accountTotal(), currency));
        lines.add("Outstanding lent: "
                + money(snapshot.outstandingLent(), currency));
        lines.add("Outstanding borrowed: "
                + money(snapshot.outstandingBorrowed(), currency));
        lines.add("Net worth: " + money(snapshot.netWorth(), currency));
        lines.add("");
        lines.add("Account balances");
        for (Map.Entry<Account, BigDecimal> entry
                : snapshot.accountBalances().entrySet()) {
            lines.add("  " + entry.getKey().getDisplayName() + ": "
                    + money(entry.getValue(), currency));
        }
        lines.add("");
        lines.add("Category spending");
        for (Map.Entry<Category, BigDecimal> entry
                : report.getExpensesByCategory().entrySet()) {
            lines.add("  " + entry.getKey().getDisplayName() + ": "
                    + money(entry.getValue(), currency));
        }
        lines.add("");
        RecurringCommitmentSummary recurring = snapshot.recurringCommitments();
        lines.add("Recurring commitments (nominal scheduled amounts)");
        lines.add("  Income: "
                + money(recurring.scheduledIncome(), currency));
        lines.add("  Expenses: "
                + money(recurring.scheduledExpenses(), currency));
        lines.add("  Transfers: "
                + money(recurring.scheduledTransfers(), currency));
        if (lines.size() > MAX_DETAIL_LINES) {
            lines = new ArrayList<>(lines.subList(0, MAX_DETAIL_LINES));
            lines.add("Additional detail omitted; use CSV export for full data.");
        }
        return List.copyOf(lines);
    }

    private static byte[] createPdf(List<String> lines) {
        StringBuilder content = new StringBuilder("BT\n/F1 11 Tf\n50 790 Td\n");
        for (int index = 0; index < lines.size(); index++) {
            if (index > 0) content.append("0 -17 Td\n");
            content.append('(').append(pdfText(lines.get(index)))
                    .append(") Tj\n");
        }
        content.append("ET\n");
        byte[] stream = content.toString().getBytes(StandardCharsets.US_ASCII);
        List<byte[]> objects = List.of(
                ascii("<< /Type /Catalog /Pages 2 0 R >>"),
                ascii("<< /Type /Pages /Kids [3 0 R] /Count 1 >>"),
                ascii("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 842] "
                        + "/Resources << /Font << /F1 5 0 R >> >> "
                        + "/Contents 4 0 R >>"),
                concat(ascii("<< /Length " + stream.length + " >>\nstream\n"),
                        stream, ascii("endstream")),
                ascii("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.writeBytes(ascii("%PDF-1.4\n%SpendWise\n"));
        List<Integer> offsets = new ArrayList<>();
        for (int index = 0; index < objects.size(); index++) {
            offsets.add(output.size());
            output.writeBytes(ascii((index + 1) + " 0 obj\n"));
            output.writeBytes(objects.get(index));
            output.writeBytes(ascii("\nendobj\n"));
        }
        int xref = output.size();
        output.writeBytes(ascii("xref\n0 " + (objects.size() + 1)
                + "\n0000000000 65535 f \n"));
        for (int offset : offsets) {
            output.writeBytes(ascii(String.format("%010d 00000 n \n", offset)));
        }
        output.writeBytes(ascii("trailer\n<< /Size " + (objects.size() + 1)
                + " /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF\n"));
        return output.toByteArray();
    }

    private static String pdfText(String value) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '(' || character == ')' || character == '\\') {
                result.append('\\').append(character);
            } else if (character >= 32 && character <= 126) {
                result.append(character);
            } else {
                result.append('?');
            }
        }
        return result.toString();
    }

    private static String money(BigDecimal value, String currency) {
        return currency + " " + value.toPlainString();
    }

    private static String requireText(String value, String field) {
        String cleaned = Objects.requireNonNull(value, field + " is required.")
                .strip();
        if (cleaned.isEmpty()) throw new IllegalArgumentException(
                field + " cannot be blank.");
        return cleaned;
    }

    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (byte[] part : parts) output.writeBytes(part);
        return output.toByteArray();
    }
}
