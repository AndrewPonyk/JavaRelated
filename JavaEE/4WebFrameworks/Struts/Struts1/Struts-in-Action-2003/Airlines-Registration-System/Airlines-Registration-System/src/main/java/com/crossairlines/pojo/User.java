package com.crossairlines.pojo;

import java.io.Serializable;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

public class User implements Serializable {

    private static final long serialVersionUID = -3144111287278343486L;
    private int userAuthId;
    private String firstName;
    private String middleName;
    private String lastName;
    private long mobileNo;
    private String address;
    private String email;
    private Date dob = null;
    private String password;
    private boolean isAdmin;

    private Set<BookTicket> bookTicket = new HashSet<BookTicket>();

    public Set<BookTicket> getBookTicket() {
        return bookTicket;
    }
    public void setBookTicket(Set<BookTicket> bookTicket) {
        this.bookTicket = bookTicket;
    }
    public Date getDob() {
        return dob;
    }
    public void setDob(Date dob) {
        this.dob = dob;
    }
    public String getEmail() {
        return email;
    }
    public void setEmail(String email) {
        this.email = email;
    }
    public String getPassword() {
        return password;
    }
    public void setPassword(String password) {
        this.password = password;
    }
    public boolean getIsAdmin() {
        return isAdmin;
    }
    public void setIsAdmin(boolean isAdmin) {
        this.isAdmin = isAdmin;
    }

    public int getUserAuthId() {
        return userAuthId;
    }
    public void setUserAuthId(int userId) {
        this.userAuthId = userId;
    }
    public String getFirstName() {
        return firstName;
    }
    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }
    public String getMiddleName() {
        return middleName;
    }
    public void setMiddleName(String middleName) {
        this.middleName = middleName;
    }
    public String getLastName() {
        return lastName;
    }
    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public long getMobileNo() {
        return mobileNo;
    }
    public void setMobileNo(long mobileno) {
        this.mobileNo = mobileno;
    }
    public String getAddress() {
        return address;
    }
    public void setAddress(String address) {
        this.address = address;
    }
    @Override
    public String toString() {
        return "User [userAuthId=" + userAuthId + ", firstName=" + firstName + ", middleName=" + middleName
                + ", lastName=" + lastName + ", mobileNo=" + mobileNo + ", address=" + address + ", email=" + email
                + ", dob=" + dob + ", password=" + password + ", isAdmin=" + isAdmin + ", bookTicket=" + bookTicket
                + "]";
    }


}