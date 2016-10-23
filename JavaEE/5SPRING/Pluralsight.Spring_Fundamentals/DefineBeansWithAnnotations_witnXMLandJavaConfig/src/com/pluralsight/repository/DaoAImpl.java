package com.pluralsight.repository;

import org.springframework.stereotype.Repository;

@Repository("daoARepository")
public class DaoAImpl implements DaoA {

    public String getTestString(){
        return "test";
    }
}
