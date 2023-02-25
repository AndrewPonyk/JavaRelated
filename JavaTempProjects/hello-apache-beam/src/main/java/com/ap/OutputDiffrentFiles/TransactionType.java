package com.ap.OutputDiffrentFiles;

import java.util.Arrays;
import java.util.List;

public enum TransactionType {
    DEPOSIT,
    WITHDRAWAL,
    TRANSFER;

    List<String> getFieldNames(){
        return Arrays.asList("a", "b");
    }
    List<String> getAllFields(BankTransaction tx){
        return Arrays.asList("Amount");
    }
}
