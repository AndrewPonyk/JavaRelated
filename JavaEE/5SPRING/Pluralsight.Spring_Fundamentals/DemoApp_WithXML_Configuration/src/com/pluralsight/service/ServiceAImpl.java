package com.pluralsight.service;

import com.pluralsight.repository.DaoA;
import org.springframework.beans.factory.annotation.Autowired;

public class ServiceAImpl implements ServiceA {

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
