package com.ap;

import org.apache.commons.lang3.text.StrSubstitutor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Created by aponyk on 05.08.2016.
 */
public class ModifyWordDoc {
    public static void main(String[] args) throws IOException {
        System.err.println("Modify word document");
        XWPFDocument doc = new XWPFDocument(new FileInputStream("/home/andrii/temp/ApachePoiExamples/letter-template.docx"));
        Map<String, String> values = new HashMap<>();
        values.put("S2_R1_FName", "Roman");
        values.put("S2_R1_MI", "Romanenko2");
        values.put("S2_R1_LName", "Romanenko4");
        values.put("agreement.amount", "1000 $");
        values.put("agreement.amount.text", "One thousand dolars");


        StrSubstitutor sub = new StrSubstitutor(values, "${", "}");

        doc.getParagraphs().forEach(p -> {

            p.getRuns().forEach(r -> {
                Optional.of(r).ifPresent(run -> {
                    String text = run.getText(0);
                    if (text != null) {
                        String result = sub.replace(text);
                        run.setText(result, 0);
                    }
                });
            });
        });
        doc.write(new FileOutputStream("/home/andrii/temp/ApachePoiExamples/TestDocWithPlaceholders_REPLACED.docx"));
        doc.close();
    }
}
