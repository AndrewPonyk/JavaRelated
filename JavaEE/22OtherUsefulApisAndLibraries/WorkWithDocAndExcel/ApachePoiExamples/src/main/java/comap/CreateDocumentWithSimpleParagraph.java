package com.ap;

import org.apache.poi.xwpf.usermodel.Borders;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Created by aponyk on 05.08.2016.
 */
public class CreateDocumentWithSimpleParagraph {
    public static void main(String[] args) throws IOException {
        //D:\temp
        XWPFDocument document = new XWPFDocument();
        FileOutputStream out = new FileOutputStream(
                new File("D:\\temp\\createdocument.docx"));
        //create Paragraph
        XWPFParagraph paragraph = document.createParagraph();
        XWPFRun run=paragraph.createRun();
        run.setFontFamily("Castellar");
        run.setFontSize(14);
        run.setText("At tutorialspoint.com, we strive hard to " +
                        "provide quality tutorials for self-learning " +
                        "purpose in the domains of Academics, Information " +
                        "Technology, Management and Computer Programming Languages.");
        paragraph.setBorderLeft(Borders.BASIC_BLACK_SQUARES);
        paragraph.setBorderTop(Borders.BASIC_BLACK_DASHES);

        document.write(out);
        out.close();
        System.out.println("createdocument.docx written successully");

        document.close();
    }
}
