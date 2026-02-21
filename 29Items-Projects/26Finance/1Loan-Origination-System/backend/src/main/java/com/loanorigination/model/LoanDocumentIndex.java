package com.loanorigination.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;

import java.time.LocalDateTime;

@Document(indexName = "loan_documents")
@Setting(shards = 1, replicas = 0)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanDocumentIndex {

    @Id
    private String id;

    @Field(type = FieldType.Long)
    private Long documentId;

    @Field(type = FieldType.Long)
    private Long applicationId;

    @Field(type = FieldType.Keyword)
    private String documentType;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String documentName;

    @Field(type = FieldType.Keyword)
    private String mimeType;

    @Field(type = FieldType.Date)
    private LocalDateTime uploadedAt;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String ocrText;
}
