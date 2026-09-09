package com.hyperlocal.delivery.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.hyperlocal.delivery.dto.reports.RegisterRowDto;

class CsvExportWriterTest {

    @Test
    void testFormulaInjectionGuardOnCustomerName() {
        // Test that a field starting with = is prefixed with a single quote to prevent formula execution
        var evilCustomerName = "=HYPERLINK(\"http://evil.com\",\"click\")";
        var rows = List.of(
                new RegisterRowDto("token1", null, evilCustomerName, "123 Main St", "Agent John", null, null));

        var csv = CsvExportWriter.register(rows);

        // Verify that the output contains the guarded version: the field should be quoted with the leading '
        assertTrue(csv.contains("\"'=HYPERLINK"),
                "CSV should prefix formula-injection characters with single quote");
        assertFalse(csv.contains("\"=HYPERLINK"),
                "CSV should NOT have unguarded formula character");
    }

    @Test
    void testFormulaInjectionGuardOnPlusSign() {
        var rows = List.of(
                new RegisterRowDto("token1", null, "+2 Additional Info", "123 Main St", "Agent John", null, null));

        var csv = CsvExportWriter.register(rows);

        assertTrue(csv.contains("\"'+2 Additional"),
                "CSV should prefix + with single quote");
    }

    @Test
    void testFormulaInjectionGuardOnMinusSign() {
        var rows = List.of(
                new RegisterRowDto("token1", null, "Normal Name", "-1234 Negative", "Agent John", null, null));

        var csv = CsvExportWriter.register(rows);

        assertTrue(csv.contains("\"'-1234"),
                "CSV should prefix - with single quote in address field");
    }

    @Test
    void testFormulaInjectionGuardOnAtSign() {
        var rows = List.of(
                new RegisterRowDto("token1", null, "Normal Name", "123 Main St", "@SUM(A1:A10)", null, null));

        var csv = CsvExportWriter.register(rows);

        assertTrue(csv.contains("\"'@SUM"),
                "CSV should prefix @ with single quote");
    }

    @Test
    void testNormalValuesAreUnaffected() {
        var rows = List.of(
                new RegisterRowDto("token1", null, "Jamie Customer", "456 Oak Ave", "Agent Alice", null, null));

        var csv = CsvExportWriter.register(rows);

        assertTrue(csv.contains("\"Jamie Customer\""),
                "Normal customer names should not be modified");
        assertTrue(csv.contains("\"456 Oak Ave\""),
                "Normal addresses should not be modified");
        assertTrue(csv.contains("\"Agent Alice\""),
                "Normal agent names should not be modified");
    }

    @Test
    void testQuotedInternalQuotes() {
        // Ensure existing RFC 4180 quoting still works
        var rows = List.of(
                new RegisterRowDto("token1", null, "Smith \"The Boss\" Jr.", "123 \"Main\" St", "Agent John", null, null));

        var csv = CsvExportWriter.register(rows);

        // Internal quotes should be doubled and the whole field quoted
        assertTrue(csv.contains("\"Smith \"\"The Boss\"\" Jr.\""),
                "Internal quotes should be doubled");
        assertTrue(csv.contains("\"123 \"\"Main\"\" St\""),
                "Address internal quotes should be doubled");
    }

    @Test
    void testFormulaCharacterWithInternalQuotes() {
        var rows = List.of(
                new RegisterRowDto("token1", null, "=COMMAND(\"whoami\")", "123 Main St", "Agent John", null, null));

        var csv = CsvExportWriter.register(rows);

        // Should have the guard character prefix AND internal quotes doubled
        assertTrue(csv.contains("\"'=COMMAND(\"\"whoami\"\")\""),
                "Should guard formula and escape internal quotes");
    }

    @Test
    void testEmptyAndNullValues() {
        var rows = List.of(
                new RegisterRowDto("token1", null, "", "123 Main St", null, null, null));

        var csv = CsvExportWriter.register(rows);

        // Empty and null should not cause issues
        assertDoesNotThrow(() -> CsvExportWriter.register(rows));
        assertTrue(csv.contains("token1"));
    }

    @Test
    void testHeaderRow() {
        var csv = CsvExportWriter.register(List.of());

        // Verify header is present
        assertTrue(csv.contains("token,status,customerName,address,agentName,scheduledAt,deliveredAt"));
    }
}
