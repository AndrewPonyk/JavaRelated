package com.ap.xactus;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.AcroFields;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfStamper;

import java.io.FileOutputStream;
import java.io.IOException;

public class TryToHideUnnesessaryRowsAndInputs {
    public static void main(String[] args) throws DocumentException, IOException {
        System.out.println("Hello world!");
        // Path to the PDF template
        String templatePath = "C:\\mygit\\JavaRelated\\JavaEE\\18XML_JSON_PDF\\!!Work-With-Pdf-Java\\hello-world-java-integrate-pdf\\DVX Output Form (5).pdf";

        // Path to the output PDF
        String outputPath = "C:\\mygit\\JavaRelated\\JavaEE\\18XML_JSON_PDF\\!!Work-With-Pdf-Java\\hello-world-java-integrate-pdf\\DVX Output Form (5).pdf--FILLED.pdf";

        // Read the template PDF
        PdfReader reader = new PdfReader(templatePath);

        // Create a stamper
        PdfStamper stamper = new PdfStamper(reader, new FileOutputStream(outputPath));

        // Get the form fields
        AcroFields form = stamper.getAcroFields();

        // Fill the form fields
       // form.setField("ClientName", "value1!!!!");
        //form.setField("placeholder2", "value2!!!!");


        // Get the direct content
        //PdfContentByte cb = stamper.getOverContent(1); // assuming you want to modify the first page

        // Create a white rectangle
       // Rectangle rectangle = new Rectangle(50, 500, 550, 600); // adjust these coordinates as needed to cover the unused table rows
        //rectangle.setBackgroundColor(new BaseColor(0, 255, 0)); // white color

        // Add the rectangle to the PDF
        //cb.rectangle(rectangle);

        // Close the stamper
        stamper.close();
        reader.close();

    }
}
