package fr.expand.project.importdata.imports;

import static org.junit.Assert.*;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ImportWorkflowTest {
    private TabularFile.Table csv(String text) {
        return TabularFile.csv(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void quotedMultilineBomAndZeroIdentity() {
        var table =
                csv(
                        "\uFEFFid;name;note\r\n"
                                + "0;\"O'Brien; Alice\";\"line1\n"
                                + "line2 \"\"quoted\"\"\"\r\n"
                                + "1;Bob;plain\r\n");
        assertEquals(List.of("id", "name", "note"), table.columns());
        assertEquals(List.of(2, 4), table.rowNumbers());
        assertEquals("line1\nline2 \"quoted\"", table.rows().get(0).get(2));
        var input = ImportInput.table(table, "PERSON", "id", Map.of("NAME", "name"));
        assertEquals(Integer.valueOf(0), input.entries().get(0).dataId());
        assertEquals("O'Brien; Alice", input.entries().get(0).attributes().get("NAME"));
    }

    @Test
    public void invalidIdentitiesStayLocalized() {
        var input =
                ImportInput.table(
                        csv("id,name\n-1,A\n1.5,B\n2147483648,C\n0,D"),
                        "PERSON",
                        "id",
                        Map.of("NAME", "name"));
        assertEquals(4, input.entries().size());
        for (int i = 0; i < 3; i++) assertFalse(input.entries().get(i).errors().isEmpty());
        assertTrue(input.entries().get(3).errors().isEmpty());
    }

    @Test
    public void rejectsAmbiguousOrBrokenCsv() {
        for (String value :
                List.of(
                        "id,id\n1,2",
                        "id,name\n1,\"unclosed",
                        "id,name\n1,\"a\"junk",
                        "id,name\n1,2,3"))
            assertThrows(IllegalArgumentException.class, () -> csv(value));
        assertThrows(
                IllegalArgumentException.class, () -> TabularFile.csv(new byte[] {(byte) 0xff}));
    }

    @Test
    public void enforcesRowLimit() {
        assertThrows(IllegalArgumentException.class, () -> csv("id,name\n" + "1,A\n".repeat(5001)));
    }

    @Test
    public void xlsxSheetAndDatesAndFormulaRejection() throws Exception {
        byte[] bytes;
        try (var workbook = new XSSFWorkbook();
                var output = new ByteArrayOutputStream()) {
            workbook.createSheet("Unused").createRow(0).createCell(0).setCellValue("ignored");
            var sheet = workbook.createSheet("People");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("id");
            header.createCell(1).setCellValue("name");
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue(0);
            row.createCell(1).setCellValue("Alice");
            workbook.write(output);
            bytes = output.toByteArray();
            var table = TabularFile.read(bytes, "xlsx", "People");
            assertEquals(List.of("Unused", "People"), table.sheets());
            assertEquals(List.of("0", "Alice"), table.rows().get(0));
            row.getCell(1).setCellFormula("1+1");
            output.reset();
            workbook.write(output);
            bytes = output.toByteArray();
        }
        byte[] formula = bytes;
        assertThrows(
                IllegalArgumentException.class, () -> TabularFile.read(formula, "xlsx", "People"));
    }

    @Test
    public void xmlIncludesRelationshipIdentityAndAttributes() throws Exception {
        var input =
                ImportInput.xml(
                        "<DATAS><OBJECTS><OBJECT TYPE=\"PERSON\" ID=\"0\"><ATTRIBUTE KEY=\"NAME\""
                            + " VALUE=\"Alice\"/></OBJECT></OBJECTS><LINKS><LINK"
                            + " TYPE=\"KNOWS\"><ATTRIBUTE KEY=\"NOTE\""
                            + " VALUE=\"friend\"/><OBJ_LINK_A TYPE=\"PERSON\" ID=\"0\"/><OBJ_LINK_B"
                            + " TYPE=\"PERSON\" ID=\"1\"/></LINK></LINKS></DATAS>");
        assertEquals(2, input.entries().size());
        assertEquals("PERSON/0", input.entries().get(1).from().key());
        assertEquals("friend", input.entries().get(1).attributes().get("NOTE"));
    }
}
