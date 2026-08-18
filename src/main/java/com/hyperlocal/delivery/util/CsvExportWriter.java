package com.hyperlocal.delivery.util;

import java.util.List;

import com.hyperlocal.delivery.dto.reports.AgentPerformanceRowDto;
import com.hyperlocal.delivery.dto.reports.RegisterRowDto;

/**
 * Builds the raw CSV bodies served by {@code ReportController}'s
 * {@code /api/reports/{kind}/export} endpoint.
 *
 * <p>Reuses the same row DTOs ({@link AgentPerformanceRowDto},
 * {@link RegisterRowDto}) as the corresponding JSON list endpoints, so the
 * CSV columns are guaranteed to match the field names/order already
 * exposed to the frontend rather than duplicating the shaping logic.
 *
 * <p>Plain {@link StringBuilder} construction with RFC 4180-style quoting
 * (wrap in double quotes, escape embedded quotes by doubling them) — no
 * new CSV dependency needed for this scope.
 */
public final class CsvExportWriter {

    private CsvExportWriter() {
    }

    public static String agentPerformance(List<AgentPerformanceRowDto> rows) {
        StringBuilder sb = new StringBuilder("id,name,active,assigned,delivered,failed,returned,open,avgHours,perDay\n");
        for (AgentPerformanceRowDto r : rows) {
            sb.append(String.join(",",
                    String.valueOf(r.id()),
                    quote(r.name()),
                    String.valueOf(r.active()),
                    String.valueOf(r.assigned()),
                    String.valueOf(r.delivered()),
                    String.valueOf(r.failed()),
                    String.valueOf(r.returned()),
                    String.valueOf(r.open()),
                    String.valueOf(r.avgHours()),
                    String.valueOf(r.perDay())))
                    .append("\n");
        }
        return sb.toString();
    }

    public static String register(List<RegisterRowDto> rows) {
        StringBuilder sb = new StringBuilder("token,status,customerName,address,agentName,scheduledAt,deliveredAt\n");
        for (RegisterRowDto r : rows) {
            sb.append(String.join(",",
                    quote(r.token()),
                    r.status() != null ? r.status().getWireValue() : "",
                    quote(r.customerName()),
                    quote(r.address()),
                    quote(r.agentName()),
                    quote(r.scheduledAt()),
                    quote(r.deliveredAt())))
                    .append("\n");
        }
        return sb.toString();
    }

    private static String quote(String value) {
        if (value == null) {
            return "";
        }
        // Guard against CSV formula injection: if a field starts with a formula character,
        // prefix it with a single quote to force the spreadsheet to treat it as literal text.
        // See: https://owasp.org/www-community/attacks/CSV_Injection
        if (!value.isEmpty() && isFormulaCharacter(value.charAt(0))) {
            value = "'" + value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static boolean isFormulaCharacter(char c) {
        return c == '=' || c == '+' || c == '-' || c == '@';
    }
}
