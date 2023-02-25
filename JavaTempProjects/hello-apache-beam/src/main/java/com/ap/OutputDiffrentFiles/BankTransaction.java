package com.ap.OutputDiffrentFiles;

import java.io.Serializable;

public class BankTransaction implements Serializable {
    String data;
    String typeName;

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public String getTypeName() {
        return typeName;
    }

    public void setTypeName(String typeName) {
        this.typeName = typeName;
    }
}
