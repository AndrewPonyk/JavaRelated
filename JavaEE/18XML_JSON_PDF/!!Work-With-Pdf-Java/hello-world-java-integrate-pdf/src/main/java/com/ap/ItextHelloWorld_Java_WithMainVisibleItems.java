package com.ap;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.pdf.draw.LineSeparator;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

public class ItextHelloWorld_Java_WithMainVisibleItems {
    public static void main(String[] args) throws DocumentException, IOException {

        createPdf("helloWorld1.pdf");
    }

    public static void createPdf(String filename) throws DocumentException, IOException {
        Document document = new Document();
        PdfWriter.getInstance(document, new FileOutputStream(filename));
        document.open();

        //add text
        document.add(new Paragraph("Hello, World!"));

        // Create a Chunk with red background
        Chunk redBackgroundChunk = new Chunk("This is a label with red background");
        redBackgroundChunk.setBackground(BaseColor.RED);
        // Add the Chunk to the document
        document.add(redBackgroundChunk);

        // Add a new line
        document.add(new Paragraph(" "));

        // Add a horizontal line
        LineSeparator line = new LineSeparator();
        line.setLineColor(new BaseColor(128, 128, 128)); // Set color to grey
        document.add(new Chunk(line));

        // Add a List
        List list = new List(true, false, 10);
        list.add(new ListItem("First item"));
        list.add(new ListItem("Second item"));
        document.add(list);


        // Add an Image
        try {
            Image image = Image.getInstance(new URL("https://upload.wikimedia.org/wikipedia/commons/thumb/1/1f/Wikipedia-logo-v2-uk.svg/800px-Wikipedia-logo-v2-uk.svg.png"));
            // Scale image
            float width = 200; // width in pixels
            float height = image.getHeight() * width / image.getWidth(); // calculate the height to maintain the aspect ratio
            image.scaleAbsolute(width, height);
            document.add(image);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }

        ////////////////////////////////////////////Begin of TABLe
        // Create a table with 3 columns
        PdfPTable table = new PdfPTable(3);

        // Define colors for header and rows
        BaseColor headerColor = new BaseColor(128, 128, 128); // Grey color for header
        BaseColor oddRowColor = new BaseColor(200, 200, 200); // Light grey color for odd rows
        BaseColor evenRowColor = BaseColor.WHITE; // White color for even rows

        // Add header cells
        PdfPCell cell = new PdfPCell(new Phrase("Header 1"));
        cell.setBackgroundColor(headerColor);
        table.addCell(cell);

        cell = new PdfPCell(new Phrase("Header 2"));
        cell.setBackgroundColor(headerColor);
        table.addCell(cell);

        cell = new PdfPCell(new Phrase("Header 3"));
        cell.setBackgroundColor(headerColor);
        table.addCell(cell);

        // Add data rows
        for (int i = 1; i <= 10; i++) {
            for (int j = 1; j <= 3; j++) {
                cell = new PdfPCell(new Phrase("Row " + i + ", Column " + j));
                cell.setBackgroundColor(i % 2 == 0 ? evenRowColor : oddRowColor);
                table.addCell(cell);
            }
        }
        // Add the table to the document
        document.add(table);
        ////////////////////////////////////////////End of TABLe
        document.close();
    }

    public static void createPdfMemory(String filename) throws DocumentException, IOException {

        Document document = new Document();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter.getInstance(document, baos);

        document.open();
        document.add(new Paragraph("Hello, World!"));
        document.close();

        // will save it on a file just for testing
        FileOutputStream fos = new FileOutputStream(filename);
        fos.write(baos.toByteArray());
        fos.close();
    }
}
