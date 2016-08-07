package com.ap;

import org.apache.poi.xwpf.usermodel.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Created by aponyk on 05.08.2016.
 */
public class CreateDocumentWithTable {

    public static void main(String[] args) throws IOException {
        //D:\temp
        XWPFDocument document = new XWPFDocument();
        FileOutputStream out = new FileOutputStream(
                new File("D:\\temp\\createdocument_withTables.docx"));

        XWPFTable table = document.createTable();
        XWPFTableRow tableRowOne = table.getRow(0);
        tableRowOne.getCell(0).setText("first cell");
        tableRowOne.addNewTableCell().setText("second cell");

        XWPFTableRow tableRowTwo = table.createRow();
        tableRowTwo.getCell(0).setText("third cell");
        tableRowTwo.getCell(1).setText("fourth cell");


        document.write(out);
        out.close();
        System.out.println("createdocument_withTables.docx written successully");

        document.close();
    }
}
