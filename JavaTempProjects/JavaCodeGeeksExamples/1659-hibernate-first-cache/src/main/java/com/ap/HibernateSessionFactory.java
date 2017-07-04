package com.ap;

import org.hibernate.HibernateException;
import org.hibernate.SessionFactory;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Configuration;
import org.hibernate.service.ServiceRegistry;

/**
 * @author Andres.Cespedes
 * @version 1.0 $Date: 11/02/2015
 * @since 1.7
 *
 */
public class HibernateSessionFactory {

    private static SessionFactory sessionFactory;

    // Static block for initialization
    static {
        try {
            // Create the Configuration object from hibernate.cfg.xml
            Configuration configuration = new Configuration().configure();
            // New way to create a Session Factory in Hibernate4
            StandardServiceRegistryBuilder serviceRegistryBuilder = new StandardServiceRegistryBuilder();
            // Enforces to set the configuration
            serviceRegistryBuilder.applySettings(configuration.getProperties());
            ServiceRegistry serviceRegistry = serviceRegistryBuilder.build();
            // with the serviceRegistry creates a new Factory, and sets
            setSessionFactory(configuration
                    .buildSessionFactory(serviceRegistry));
        } catch (HibernateException he) {
            System.err
                    .println("There was an error while creating the SessionFactory: "
                            + he.getMessage());
        }
    }

    /**
     * @return the sessionFactory
     */
    public static SessionFactory getSessionFactory() {
        return sessionFactory;
    }

    /**
     * @param sessionFactory
     *            the sessionFactory to set
     */
    public static void setSessionFactory(SessionFactory sessionFactory) {
        HibernateSessionFactory.sessionFactory = sessionFactory;
    }

}
