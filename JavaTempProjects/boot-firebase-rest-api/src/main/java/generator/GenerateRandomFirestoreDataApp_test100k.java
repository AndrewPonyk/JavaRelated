package generator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.core.ApiFuture;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteBatch;
import com.google.cloud.firestore.WriteResult;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;

import java.io.FileInputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

public class GenerateRandomFirestoreDataApp_test100k {
    public static final String COLLECTION_NAME = "test100k";
    public static ObjectMapper objectMapper = new ObjectMapper();

    static {
    }

    public static void populate() throws ExecutionException, JsonProcessingException {
        System.out.println("GENERATE firestore data");
        var dbFirestore = FirestoreClient.getFirestore();

        List<Test100k> batchList = new LinkedList<>();
        for (int i = 0; i < 200000; i++) {
            Test100k row = new Test100k();
            row.setCount(i);
            row.setName(i  + "Name");
            row.setLine1("Line1-" + i+ "<<" + UUID.randomUUID());
            row.setLine2("Line2-" + i+ "<<" + UUID.randomUUID());
            row.setLine3("Line3-" + i+ "<<" + UUID.randomUUID());
            row.setLine4("Line4-" + i + "<<" + UUID.randomUUID());
            row.setLine5("Line5-" + i + "<<" + UUID.randomUUID());
            row.setData(objectMapper.writeValueAsString(row));
            batchList.add(row);

            if(batchList.size() == 100) {
                System.out.println("Write batch starting from" + i);
                commitBatch(dbFirestore, batchList);
                batchList.clear();
            }
        }
        commitBatch(dbFirestore, batchList);
    }

    private static void commitBatch(Firestore dbFirestore, List<Test100k> batchList) {
        WriteBatch batch = dbFirestore.batch();
        for (Test100k row: batchList) {
            DocumentReference rowRef = dbFirestore.collection(COLLECTION_NAME).document(row.getName());
            batch.set(rowRef, row);
        }
        batch.commit();
    }

    public static class Test100k {
        private String name;
        private Integer count;
        private String data; //json

        private String line1;
        private String line2;
        private String line3;
        private String line4;
        private String line5;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Integer getCount() {
            return count;
        }

        public void setCount(Integer count) {
            this.count = count;
        }

        public String getData() {
            return data;
        }

        public void setData(String data) {
            this.data = data;
        }

        public String getLine1() {
            return line1;
        }

        public void setLine1(String line1) {
            this.line1 = line1;
        }

        public String getLine2() {
            return line2;
        }

        public void setLine2(String line2) {
            this.line2 = line2;
        }

        public String getLine3() {
            return line3;
        }

        public void setLine3(String line3) {
            this.line3 = line3;
        }

        public String getLine4() {
            return line4;
        }

        public void setLine4(String line4) {
            this.line4 = line4;
        }

        public String getLine5() {
            return line5;
        }

        public void setLine5(String line5) {
            this.line5 = line5;
        }
    }

    private static void init() {
        FileInputStream serviceAccount =
                null;
        try {
            serviceAccount = new FileInputStream("./serviceAccountKey.json");
            FirebaseOptions options = new FirebaseOptions.Builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();
            FirebaseApp.initializeApp(options);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
