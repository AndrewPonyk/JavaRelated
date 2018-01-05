package com.logicbig.example;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@ConfigurationProperties("app")
public class MyAppProperties {

    //https://stackoverflow.com/questions/12576156/reading-a-list-from-properties-file-and-load-with-spring-annotation-value/29970335#29970335
    @Value("${my.list.of.ints}")
    private List<String> myList;

    private List<String> adminEmails;
    boolean sendEmailOnErrors;
    boolean exitOnErrors;
    private int refreshRate;
    private Map<String, String> orderScreenProperties;

    private Map<String, Map<String, String>> customerScreenProperties;

    public int getRefreshRate() {
        return refreshRate;
    }

    public void setRefreshRate(int refreshRate) {
        this.refreshRate = refreshRate;
    }

    public List<String> getAdminEmails() {
        return adminEmails;
    }

    public void setAdminEmails(List<String> adminEmails) {
        this.adminEmails = adminEmails;
    }

    public Map<String, String> getOrderScreenProperties() {
        return orderScreenProperties;
    }

    public void setOrderScreenProperties(Map<String, String> orderScreenProperties) {
        this.orderScreenProperties = orderScreenProperties;
    }

    public Map<String, Map<String, String>> getCustomerScreenProperties() {
        return customerScreenProperties;
    }

    public void setCustomerScreenProperties(Map<String, Map<String, String>> customerScreenProperties) {
        this.customerScreenProperties = customerScreenProperties;
    }

    public boolean isSendEmailOnErrors() {
        return sendEmailOnErrors;
    }

    public void setSendEmailOnErrors(boolean sendEmailOnErrors) {
        this.sendEmailOnErrors = sendEmailOnErrors;
    }

    public boolean isExitOnErrors() {
        return exitOnErrors;
    }

    public void setExitOnErrors(boolean exitOnErrors) {
        this.exitOnErrors = exitOnErrors;
    }

    @Override
    public String toString() {
        return "MyAppProperties{" +
                "\n adminEmails=" + adminEmails +
                ",\n sendEmailOnErrors=" + sendEmailOnErrors +
                ",\n exitOnErrors=" + exitOnErrors +
                ",\n refreshRate=" + refreshRate +
                ",\n orderScreenProperties=" + orderScreenProperties +
                ",\n customerScreenProperties=" + customerScreenProperties +
                "\n}";
    }

    public List<String> getMyList() {
        return myList;
    }

    public void setMyList(List<String> myList) {
        this.myList = myList;
    }
}