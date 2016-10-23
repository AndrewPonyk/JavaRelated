package com.pluralsight.service;

import com.pluralsight.repository.DaoA;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

@Service(value = "serviceA")
@Scope("prototype")
public class ServiceAImpl implements ServiceA {

    @Autowired
    @Qualifier("daoASecondRealisationRepository")
    private DaoA daoA;

    @Override
    public String getTestString(){
        return daoA.getTestString();
    }

    public void setDaoA(DaoA daoA) {
        this.daoA = daoA;
    }
}
