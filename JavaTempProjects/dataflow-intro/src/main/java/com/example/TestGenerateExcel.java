package com.example;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;

import java.io.*;
import java.util.List;

public class TestGenerateExcel {
    public static void main(String[] args) {
        generateExcelFile("C:\\tmp\\workbook.xls");
    }

    public static void generateExcelFile(String s) {
        Workbook wb = new HSSFWorkbook();
//Workbook wb = new XSSFWorkbook();
        CreationHelper createHelper = wb.getCreationHelper();
        Sheet sheet = wb.createSheet("new sheet");
// Create a row and put some cells in it. Rows are 0 based.
        Row row = sheet.createRow(0);
// Create a cell and put a value in it.
        Cell cell = row.createCell(0);
        cell.setCellValue(1);
// Or do it on one line.
        row.createCell(1).setCellValue(1.2);
        row.createCell(2).setCellValue(
                createHelper.createRichTextString("This is a string"));
        row.createCell(3).setCellValue(true);
        // Write the output to a file

        try (OutputStream fileOut = new FileOutputStream(s)) {
            wb.write(fileOut);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static ByteArrayOutputStream generateExcelFileBytes(String s) {
        Workbook wb = new HSSFWorkbook();
        CreationHelper createHelper = wb.getCreationHelper();
        Sheet sheet = wb.createSheet("new sheet");
// Create a row and put some cells in it. Rows are 0 based.
        Row row = sheet.createRow(0);
// Create a cell and put a value in it.
        Cell cell = row.createCell(0);
        cell.setCellValue(1);
// Or do it on one line.
        row.createCell(1).setCellValue(1.2);
        row.createCell(2).setCellValue(
                createHelper.createRichTextString("This is a stringii"));
        row.createCell(3).setCellValue(true);
        // Write the output to a file

        try (ByteArrayOutputStream fileOut = new ByteArrayOutputStream()) {

            wb.write(fileOut);
            return fileOut;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static ByteArrayOutputStream generateExcelFileBytes(List<String> strings) {
        Workbook wb = new HSSFWorkbook();
        Sheet sheet = wb.createSheet("new sheet");

        for (int i =0; i<strings.size();i++){
            Row row = sheet.createRow(i);
            Cell cell = row.createCell(0);
            cell.setCellValue(strings.get(i));
        }

        // Write the output to a file
        try (ByteArrayOutputStream fileOut = new ByteArrayOutputStream()) {

            wb.write(fileOut);
            return fileOut;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }


}
