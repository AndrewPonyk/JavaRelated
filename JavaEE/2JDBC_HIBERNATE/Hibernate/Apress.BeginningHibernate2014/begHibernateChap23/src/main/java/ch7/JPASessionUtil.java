package ch7;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

import org.hibernate.Session;

import java.util.HashMap;
import java.util.Map;

public class JPASessionUtil {
    static Map<String, EntityManagerFactory> persistenceUnits = new HashMap<>();

    public static synchronized EntityManager getEntityManager(String persistenceUnitName){
    	if(!persistenceUnits.containsKey(persistenceUnitName)){
    		persistenceUnits.put(persistenceUnitName, Persistence.createEntityManagerFactory(persistenceUnitName));
    	}

    	return persistenceUnits.get(persistenceUnitName).createEntityManager();
    }

    public static Session getSession(String persistenceUnitName){
    	return getEntityManager(persistenceUnitName).unwrap(Session.class);
    }

    public static void closeEntityManagerFactory(String persistenceUnitName){
    	if(persistenceUnits.containsKey(persistenceUnitName)){
    		persistenceUnits.get(persistenceUnitName).close();
    	}
    }

    public static void closeAllEntityManagerFactories(){
    	for(String item : persistenceUnits.keySet()){
    		persistenceUnits.get(item).close();
    	}
    }

}
