package fr.expand.project.importdata.imports;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** Bounded UTF-8 CSV/XLSX parsing. Formula cells are rejected, never evaluated. */
public final class TabularFile {
    public static final int MAX_ROWS = 5000, MAX_COLUMNS = 200, MAX_BYTES = 10 * 1024 * 1024;

    public record Table(
            List<String> columns,
            List<List<String>> rows,
            List<Integer> rowNumbers,
            List<String> sheets,
            String sheet) {}

    private TabularFile() {}

    public static Table read(byte[] bytes, String format, String sheet) throws IOException {
        if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("Fichier limité à 10 Mo");
        try {
            return switch (format) {
                case "csv" -> csv(bytes);
                case "xlsx" -> xlsx(bytes, sheet);
                default -> throw new IllegalArgumentException("Format attendu : CSV, XLSX ou XML");
            };
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new IllegalArgumentException("Fichier tabulaire invalide", e);
        }
    }

    public static Table csv(byte[] bytes) {
        String input;
        try {
            input =
                    StandardCharsets.UTF_8
                            .newDecoder()
                            .decode(java.nio.ByteBuffer.wrap(bytes))
                            .toString();
        } catch (java.nio.charset.CharacterCodingException e) {
            throw new IllegalArgumentException("Le CSV doit être encodé en UTF-8");
        }
        if (input.startsWith("\uFEFF")) input = input.substring(1);
        char delimiter = delimiter(input);
        List<List<String>> rows = new ArrayList<>();
        List<Integer> rowNumbers = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false, closed = false;
        int line = 1, startLine = 1;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < input.length() && input.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        quoted = false;
                        closed = true;
                    }
                } else {
                    cell.append(c);
                    if (c == '\n') line++;
                }
            } else if (c == '"' && cell.length() == 0 && !closed) quoted = true;
            else if (c == delimiter || c == '\n' || c == '\r') {
                row.add(cell.toString());
                cell.setLength(0);
                closed = false;
                if (row.size() > MAX_COLUMNS)
                    throw new IllegalArgumentException("Maximum 200 colonnes");
                if (c != delimiter) {
                    if (c == '\r' && i + 1 < input.length() && input.charAt(i + 1) == '\n') i++;
                    append(rows, rowNumbers, row, startLine);
                    row = new ArrayList<>();
                    line++;
                    startLine = line;
                }
            } else {
                if (closed || c == '"')
                    throw new IllegalArgumentException("Guillemets CSV invalides, ligne " + line);
                cell.append(c);
            }
            if (cell.length() > 32767)
                throw new IllegalArgumentException("Cellule trop longue, ligne " + line);
        }
        if (quoted)
            throw new IllegalArgumentException("Guillemets CSV non fermés, ligne " + startLine);
        if (!row.isEmpty() || cell.length() > 0 || closed) {
            row.add(cell.toString());
            append(rows, rowNumbers, row, startLine);
        }
        return table(rows, rowNumbers, List.of(), "");
    }

    private static char delimiter(String input) {
        int[] counts = new int[3];
        char[] candidates = {',', ';', '\t'};
        boolean quoted = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '"') quoted = !quoted;
            else if (!quoted && (c == '\r' || c == '\n')) break;
            else if (!quoted) for (int j = 0; j < 3; j++) if (c == candidates[j]) counts[j]++;
        }
        int best = 0;
        for (int j = 1; j < 3; j++) if (counts[j] > counts[best]) best = j;
        return candidates[best];
    }

    private static void append(
            List<List<String>> rows, List<Integer> numbers, List<String> row, int number) {
        if (row.stream().allMatch(String::isBlank)) return;
        if (rows.size() >= MAX_ROWS + 1)
            throw new IllegalArgumentException("Maximum 5 000 lignes de données");
        rows.add(row);
        numbers.add(number);
    }

    private static Table table(
            List<List<String>> rows, List<Integer> numbers, List<String> sheets, String sheet) {
        if (rows.isEmpty()) throw new IllegalArgumentException("Fichier vide");
        List<String> columns = rows.remove(0).stream().map(String::trim).toList();
        numbers.remove(0);
        if (columns.size() > MAX_COLUMNS
                || columns.stream().anyMatch(String::isBlank)
                || new HashSet<>(columns).size() != columns.size())
            throw new IllegalArgumentException(
                    "En-têtes requis, uniques et limités à 200 colonnes");
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).size() != columns.size())
                throw new IllegalArgumentException(
                        "Nombre de colonnes incorrect, ligne " + numbers.get(i));
        }
        return new Table(columns, rows, numbers, sheets, sheet);
    }

    private static String excelDate(Cell cell) {
        var value = cell.getLocalDateTimeCellValue();
        return value.toLocalTime().equals(java.time.LocalTime.MIDNIGHT)
                ? value.toLocalDate().toString()
                : value.toString();
    }

    private static Table xlsx(byte[] bytes, String requestedSheet) throws IOException {
        // Bound decompressed size before POI builds its XML model (including unused sheets).
        long expanded = 0;
        int entries = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            byte[] buffer = new byte[8192];
            while (zip.getNextEntry() != null) {
                if (++entries > 1000) throw new IllegalArgumentException("Classeur trop complexe");
                int count;
                while ((count = zip.read(buffer)) != -1) {
                    expanded += count;
                    if (expanded > 40L * 1024 * 1024)
                        throw new IllegalArgumentException("Classeur décompressé limité à 40 Mo");
                }
            }
        }
        if (entries == 0) throw new IllegalArgumentException("Fichier XLSX invalide");
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            List<String> sheets = new ArrayList<>();
            for (Sheet s : workbook) sheets.add(s.getSheetName());
            if (sheets.isEmpty()) throw new IllegalArgumentException("Classeur vide");
            Sheet sheet =
                    requestedSheet == null || requestedSheet.isBlank()
                            ? workbook.getSheetAt(0)
                            : workbook.getSheet(requestedSheet);
            if (sheet == null) throw new IllegalArgumentException("Feuille introuvable");
            if (sheet.getLastRowNum() > MAX_ROWS)
                throw new IllegalArgumentException("Maximum 5 000 lignes de données");
            List<List<String>> rows = new ArrayList<>();
            List<Integer> numbers = new ArrayList<>();
            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            int width = -1;
            for (Row row : sheet) {
                if (row.getLastCellNum() > MAX_COLUMNS)
                    throw new IllegalArgumentException("Maximum 200 colonnes");
                if (width < 0) width = Math.max(0, row.getLastCellNum());
                List<String> cells = new ArrayList<>();
                for (int col = 0; col < Math.max(width, row.getLastCellNum()); col++) {
                    Cell cell = row.getCell(col);
                    if (cell != null
                            && (cell.getCellType() == CellType.FORMULA
                                    || cell.getCellType() == CellType.ERROR))
                        throw new IllegalArgumentException(
                                "Formule ou erreur Excel interdite, ligne "
                                        + (row.getRowNum() + 1)
                                        + ", colonne "
                                        + (col + 1));
                    String value =
                            cell == null
                                    ? ""
                                    : cell.getCellType() == CellType.NUMERIC
                                                    && DateUtil.isCellDateFormatted(cell)
                                            ? cell.getLocalDateTimeCellValue()
                                                    .toLocalDate()
                                                    .toString()
                                            : formatter.formatCellValue(cell);
                    if (value.length() > 32767)
                        throw new IllegalArgumentException("Cellule trop longue");
                    cells.add(value);
                }
                append(rows, numbers, cells, row.getRowNum() + 1);
            }
            return table(rows, numbers, sheets, sheet.getSheetName());
        } catch (org.apache.poi.ooxml.POIXMLException e) {
            throw new IllegalArgumentException("Classeur XLSX invalide", e);
        }
    }
}
