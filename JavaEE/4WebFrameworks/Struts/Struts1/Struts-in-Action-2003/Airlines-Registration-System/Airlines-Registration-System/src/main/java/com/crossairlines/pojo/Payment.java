package com.crossairlines.pojo;

public class Payment {
    private int paymentId;
    private String cardNumber;
    private String cardHolderName;
    private String expiryDate;
    private int cvv;
    private BookTicket bookTicket;
    private User user;

    public int getPaymentId() {
        return paymentId;
    }
    public void setPaymentId(int paymentId) {
        this.paymentId = paymentId;
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
    public int getCvv() {
        return cvv;
    }
    public void setCvv(int cvv) {
        this.cvv = cvv;
    }
    public BookTicket getBookTicket() {
        return bookTicket;
    }
    public void setBookTicket(BookTicket bookTicket) {
        this.bookTicket = bookTicket;
    }
    public User getUser() {
        return user;
    }
    public void setUser(User user) {
        this.user = user;
    }



}