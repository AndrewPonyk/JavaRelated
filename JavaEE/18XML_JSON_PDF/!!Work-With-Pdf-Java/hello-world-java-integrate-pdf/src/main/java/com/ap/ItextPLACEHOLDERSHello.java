package com.ap;

import com.itextpdf.text.DocumentException;
import com.itextpdf.text.pdf.AcroFields;
import com.itextpdf.text.pdf.PdfFormField;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfStamper;

import java.io.FileOutputStream;
import java.io.IOException;

public class ItextPLACEHOLDERSHello {
    public static void main(String[] args) throws IOException, DocumentException {
        // Path to the PDF template
        String templatePath = "C:\\mygit\\JavaRelated\\JavaEE\\18XML_JSON_PDF\\!!Work-With-Pdf-Java\\hello-world-java-integrate-pdf\\new_PDF-FORM-with-placeholder1-and-placeholder2.pdf";

        // Path to the output PDF
        String outputPath = "C:\\mygit\\JavaRelated\\JavaEE\\18XML_JSON_PDF\\!!Work-With-Pdf-Java\\hello-world-java-integrate-pdf\\new_PDF-FORM-with-placeholder1-and-placeholder2--FILLED.pdf";

        // Read the template PDF
        PdfReader reader = new PdfReader(templatePath);

        // Create a stamper
        PdfStamper stamper = new PdfStamper(reader, new FileOutputStream(outputPath));

        // Get the form fields
        AcroFields form = stamper.getAcroFields();

        // Fill the form fields
        form.setField("placeholder1", "value1!!!!");
        form.setField("placeholder2", "value2!!!!");
        // ... fill other placeholders as needed

        // Make the fields read-only
        form.setFieldProperty("placeholder1", "setfflags", PdfFormField.FF_READ_ONLY, null);
        form.setFieldProperty("placeholder2", "setfflags", PdfFormField.FF_READ_ONLY, null);

        // Remove the border and make editable fields look like plain text (Flatten the form to remove editable fields)
        form.setFieldProperty("placeholder1", "border", 0, null);
        form.setFieldProperty("placeholder2", "border", 0, null);
        stamper.setFormFlattening(true);

        // Close the stamper
        stamper.close();
        reader.close();
    }
}
