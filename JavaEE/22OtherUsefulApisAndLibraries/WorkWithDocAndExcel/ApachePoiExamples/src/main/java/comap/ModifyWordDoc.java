package comap;

import com.sun.xml.internal.messaging.saaj.util.ByteOutputStream;
import org.apache.commons.lang3.text.StrSubstitutor;
import org.apache.poi.xwpf.usermodel.*;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.IntStream;

public class ModifyWordDoc {

    private static String startPlacehoder = "${";
    private static String endPlaceholder = "}";

    public static void main(String[] args) throws IOException {
        System.err.println("Modify word document : replace ${placeholders} by real some values");

        XWPFDocument doc = new XWPFDocument(new FileInputStream("D:\\temp\\nbis_federal_tape_data_template.docx"));
        // substitute placeholders in text
        StrSubstitutor sub = new StrSubstitutor(values, startPlacehoder, endPlaceholder);
        doc.getParagraphs().forEach(p -> {
            final StringBuilder sb = new StringBuilder();
            p.getRuns().forEach(run -> {
                String text = run.getText(0);
//                if(startPlacehoder.equals(text)){
//                    sb.append(text);
//                    text = null;
//                }
//                if(startPlacehoder.equals(sb.toString())){
//                    sb.append(text);
//                    text = null;
//                }
//                if(endPlaceholder.equals(text) && sb.toString().startsWith(startPlacehoder)){
//                    sb.append(text);
//                    text = sb.toString();
//                    sb.setLength(0);
//                }

                if (text != null) {
                    System.out.println(text);
                    String result = sub.replace(text);
                    run.setText(result, 0);
                }
            });
        });

        final List<String> placeholders = new ArrayList<String>();
        doc.getTables().forEach(tbl -> {
            tbl.getRow(1).getTableCells().forEach(cell -> {
                placeholders.add(cell.getText());
            });
            tbl.removeRow(1);

            IntStream.range(0, table1Source.size()).forEach(i -> {
                final XWPFTableRow row = tbl.createRow();
                final StrSubstitutor substitutor = new StrSubstitutor(table1Source.get(i), startPlacehoder, endPlaceholder);
                IntStream.range(0, placeholders.size()).forEach(cellIndex -> {
                    row.getCell(cellIndex).setText(substitutor.replace(placeholders.get(cellIndex)));
                });
            });
        });

        doc.write(new FileOutputStream("D:\\temp\\letter-template_REPLACED.docx"));
        doc.close();
    }

    public static Map<String, String> values = new HashMap<>();
    public static List<Map<String, String>> table1Source = new ArrayList<>();

    static {
        values.put("S2_R1_FName", "John");
        values.put("S2_R1_MI", "Doe");
        values.put("S2_R1_LName", "Smith");
        values.put("agreement.amount", "1000 $");
        values.put("agreement.amount.text", "One thousand dollars");
        values.put("AssetStatusName", "One thousand dollars");
        values.put("User Update", "AUTOTEST");

        Map<String, String> row1 = new HashMap<>();
        row1.put("document.number", "1");
        row1.put("document.name", "Document 1");
        row1.put("number.of.pages", "234");
        row1.put("nbis.5e.route.number", "ffff");

        Map<String, String> row2 = new HashMap<>();
        row2.put("document.number", "2");
        row2.put("document.name", "Document 2");
        row2.put("number.of.pages", "152");
        row2.put("NBIS 5D ROUTE NUMBER", "152212");

        Map<String, String> row3 = new HashMap<>();
        row3.put("document.number", "3");
        row3.put("document.name", "Document 3");
        row3.put("number.of.pages", "15221");
        row3.put("NBIS 5D ROUTE NUMBER", "15221");

        table1Source.add(row1);
        table1Source.add(row2);
        table1Source.add(row3);
    }
}