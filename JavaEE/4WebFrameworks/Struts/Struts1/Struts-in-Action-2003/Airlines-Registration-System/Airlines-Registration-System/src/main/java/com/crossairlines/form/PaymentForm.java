package com.crossairlines.form;

import org.apache.struts.action.ActionForm;

public class PaymentForm extends ActionForm {

    private static final long serialVersionUID = -4020217449269176332L;
    private String cardNumber;
    private String cardHolderName;
    private String expiryDate;
    private String cvvNo;
    private int amount;

    public int getAmount() {
        return amount;
    }
    public void setAmount(int amount) {
        this.amount = amount;
    }
    public String getCardNumber() {
        return cardNumber;
    }
    public void setCardNumber(String cardNumber) {
        this.cardNumber = cardNumber;
    }
    public String getCardHolderName() {
        return cardHolderName;
    }
    public void setCardHolderName(String cardHolderName) {
        this.cardHolderName = cardHolderName;
    }
    public String getExpiryDate() {
        return expiryDate;
    }
    public void setExpiryDate(String expiryDate) {
        this.expiryDate = expiryDate;
    }
    public String getCvvNo() {
        return cvvNo;
    }
    public void setCvvNo(String cvvNo) {
        this.cvvNo = cvvNo;
    }

}