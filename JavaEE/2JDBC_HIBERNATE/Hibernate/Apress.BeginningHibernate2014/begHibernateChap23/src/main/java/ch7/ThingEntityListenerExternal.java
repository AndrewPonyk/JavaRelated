package ch7;


import javax.persistence.PostPersist;

public class ThingEntityListenerExternal {

    @PostPersist
    public void postPersistThing(Object o ){
        System.out.println("External LISTENER IN THING ENTITY: postr persist");
    }
}
