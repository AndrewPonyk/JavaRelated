package com.crossairlines.form;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.struts.action.ActionForm;

public class UserForm extends ActionForm {

    private static final long serialVersionUID = 45996344836395192L;
    private int userAuthId;
    private String password;
    private String email;
    private boolean isAdmin = false;
    private String firstName;
    private String middleName;
    private String lastName;
    private String dobt;
    private Date dob = null;
    private long mobileNo;
    private String address;

    public String getDobt() {
        return dobt;
    }
    public void setDobt(String dobt) {
        try {
            if(dobt!=null)
                this.dob = new SimpleDateFormat("yyyy-MM-dd").parse(dobt);
        } catch (ParseException e) {

            e.printStackTrace();
        }
        this.dobt = dobt;
    }

    public Date getDob() {
        return dob;
    }
    public void setDob(String dob) {
        try {
            this.dob = new SimpleDateFormat("yyyy-MM-dd").parse(dob);
        } catch (ParseException e) {
            this.dob=null;
            e.printStackTrace();
        }

    }
    public int getUserAuthId() {
        return userAuthId;
    }
    public void setUserAuthId(int userAuthId) {
        this.userAuthId = userAuthId;
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
    public void setMobileNo(long mobileNo) {
        this.mobileNo = mobileNo;
    }
    public String getAddress() {
        return address;
    }
    public void setAddress(String address) {
        this.address = address;
    }
    public void setAdmin(boolean isAdmin) {
        this.isAdmin = isAdmin;
    }
    public boolean getIsAdmin() {
        return isAdmin;
    }
    public void setIsAdmin(boolean isAdmin) {
        this.isAdmin = isAdmin;
    }
    public String getPassword() {
        return password;
    }
    public void setPassword(String password) {
        this.password = password;
    }

    public String getEmail() {
        return email;
    }
    public void setEmail(String email) {
        this.email = email;
    }


    @Override
    public String toString() {
        return "UserLoginForm [userAuthId=" + userAuthId + ", password=" + password + ", email=" + email + ", isAdmin="
                + isAdmin + ", firstName=" + firstName + ", middleName=" + middleName + ", lastName=" + lastName
                + ", dob=" + dob + ", mobileNo=" + mobileNo + ", address=" + address + "]";
    }


}