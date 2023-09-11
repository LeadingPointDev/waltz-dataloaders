package org.finos.waltz_util.loader;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

public class ExcelToJSON {

    private final String excelFileName;
    private final String outputFileName;
    private final String configFileName;
    private Map<String, String> columnMappings;

    public ExcelToJSON(String inputFileName, String configFileName) throws IOException {
        this.excelFileName = inputFileName;
        this.outputFileName = inputFileName.substring(0, inputFileName.lastIndexOf(".xlsx")) + "-out.json";
        System.out.println("Input Excel file: " + excelFileName);
        System.out.println("Config File : " + configFileName);
        System.out.println("Output file name: " + outputFileName);
        this.configFileName = configFileName;
        this.columnMappings = loadColumnMappings();
    }

    private Map<String, String> loadColumnMappings() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(new File(configFileName), HashMap.class);
    }

    public List<Map<String, Object>> convertToJSON() throws Exception {
        List<Map<String, Object>> jsonArray = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(new File(excelFileName));
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rows = sheet.iterator();
            Row headerRow = rows.next();
            int numColumns = headerRow.getPhysicalNumberOfCells();

            while (rows.hasNext()) {
                Row currentRow = rows.next();
                Map<String, Object> jsonObject = new HashMap<>();

                for (int col = 0; col < numColumns; col++) {
                    Cell cell = currentRow.getCell(col);
                    String externalColumnName = headerRow.getCell(col).getStringCellValue();
                    try {
                        String internalColumnName = columnMappings.get(externalColumnName);
                        // excel may contain additional columns
                        if (internalColumnName != null) {
                            jsonObject.put(internalColumnName, getValueFromCell(cell));
                        }
                    } catch (NullPointerException e) {
                        System.out.println("No mapping for column: " + externalColumnName);
                    }
                }
                jsonArray.add(jsonObject);
            }
        }
        return jsonArray;
    }

    private String getValueFromCell(Cell cell) throws Exception {
        if (cell == null) {
            return "";
        }
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();

            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    // Convert date to desired format if needed
                    return cell.getDateCellValue().toString();
                } else {
                    double numericValue = cell.getNumericCellValue();
                    if (numericValue % 1 == 0) { // Check if numeric value is integer
                        return String.valueOf((int) numericValue);
                    } else {
                        return String.valueOf(numericValue);
                    }
                }

            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                throw new Exception("The Cell at " + cell.getAddress() + " contains a formula. This loader does not support formulas (yet).");
                //todo: add support for formula
            default:
                return "";
        }
    }


    public void saveJsonEntriesToFile(List<Map<String, Object>> jsonArray, String filename) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.writeValue(new File(filename), jsonArray);
    }


    public String convert() throws Exception {
        List<Map<String, Object>> jsonArray = convertToJSON();
        saveJsonEntriesToFile(jsonArray, outputFileName);
        return outputFileName;
    }


}
