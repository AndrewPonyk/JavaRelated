package com.mprog.listener;

import com.mprog.entity.Revision;
import org.hibernate.envers.RevisionListener;

public class MyRevisionListener implements RevisionListener {

    @Override
    public void newRevision(Object revisionEntity) {
//        SecurityContext.getUser.getId
        ((Revision) revisionEntity).setUsername("Munir");
    }
}
