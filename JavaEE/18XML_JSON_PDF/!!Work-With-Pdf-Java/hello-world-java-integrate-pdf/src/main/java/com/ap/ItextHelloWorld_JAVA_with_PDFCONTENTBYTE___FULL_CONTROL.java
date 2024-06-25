package com.ap;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class ItextHelloWorld_JAVA_with_PDFCONTENTBYTE___FULL_CONTROL {
    public static void main(String[] args) throws IOException, DocumentException {
        System.out.println("Hello world!");
        Document document = new Document();
        PdfWriter writer = PdfWriter.getInstance(document, new FileOutputStream("C:\\tmp\\simplePdfContentByteExample.pdf"));
        document.open();

        PdfContentByte cb = writer.getDirectContent();

        // Add text
        BaseFont bf = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
        cb.beginText();
        cb.setFontAndSize(bf, 12);
        cb.setTextMatrix(50, 800);
        cb.showText("Hello, World!");
        cb.endText();

        // Add filled blue rectangle
        cb.saveState();
        cb.setColorFill(BaseColor.BLUE);
        cb.rectangle(50, 750, 100, 50);
        cb.fill();
        cb.restoreState();

        // Add a simple 3x3 table
        PdfPTable table = new PdfPTable(3);
        for (int i = 0; i < 9; i++) {
            table.addCell("Cell " + (i + 1));
        }
        table.setTotalWidth(300);
        table.writeSelectedRows(0, -1, 50, 700, cb);

        document.close();
    }
}
