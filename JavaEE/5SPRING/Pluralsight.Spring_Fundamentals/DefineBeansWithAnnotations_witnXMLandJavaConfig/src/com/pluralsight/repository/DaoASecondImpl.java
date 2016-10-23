package com.pluralsight.repository;

import org.springframework.stereotype.Repository;

/**
 * Created by andrii on 23.10.16.
 */
@Repository("daoASecondRealisationRepository")
public class DaoASecondImpl implements DaoA{
    @Override
    public String getTestString() {
        return "second implementation of DaoA";
    }
}
