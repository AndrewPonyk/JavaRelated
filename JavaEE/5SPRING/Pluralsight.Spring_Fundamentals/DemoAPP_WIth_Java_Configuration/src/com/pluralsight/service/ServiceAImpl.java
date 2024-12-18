package com.pluralsight.service;

import com.pluralsight.repository.DaoA;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

public class ServiceAImpl implements ServiceA {

    @Qualifier("daoA")
    @Autowired
    private DaoA daoA;

    @Override
    public String getTestString(){
        return daoA.getTestString();
    }

    public void setDaoA(DaoA daoA) {
        this.daoA = daoA;
    }
}
