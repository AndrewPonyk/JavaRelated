package com.api.service;

import com.api.controller.Product;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteResult;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Service
public class ProductService {

    private static final String COLLECTION_NAME = "products";

    public String saveProduct(Product product) throws ExecutionException, InterruptedException {
        var dbFirestore = FirestoreClient.getFirestore();
        final ApiFuture<WriteResult> future
                = dbFirestore.collection(COLLECTION_NAME).document().set(product);

        return future.get().getUpdateTime().toString();
    }

    public List<Product> getAll() throws ExecutionException, InterruptedException {
        var dbFirestore = FirestoreClient.getFirestore();
        var querySnapshotApiFuture = dbFirestore.collection(COLLECTION_NAME).get();

        return querySnapshotApiFuture
                .get().getDocuments().stream()
                .map(document -> document.toObject(Product.class).setId(document.getId())
                )
                .collect(Collectors.toList());
    }

    public Product getProductDetails(String id) throws ExecutionException, InterruptedException {
        var dbFirestore = FirestoreClient.getFirestore();
        final DocumentReference document = dbFirestore.collection(COLLECTION_NAME).document(id);
        final ApiFuture<DocumentSnapshot> future = document.get();
        final DocumentSnapshot documentSnapshot = future.get();

        if(documentSnapshot.exists()){
            Product product = documentSnapshot.toObject(Product.class).setId(documentSnapshot.getId());
            return product;
        } else {
            return null;
        }
    }
}
