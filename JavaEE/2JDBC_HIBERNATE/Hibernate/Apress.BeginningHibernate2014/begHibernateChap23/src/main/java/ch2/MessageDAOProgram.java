package ch2;

import java.util.List;
import java.util.Properties;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.cfg.Configuration;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.service.ServiceRegistryBuilder;

public class MessageDAOProgram {

	private static SessionFactory factory;

	public static void main(String[] args) throws InterruptedException {
		System.out.println("Message Entity example");

		Configuration configuration = new Configuration();
		configuration.configure();
		ServiceRegistryBuilder srBuilder = new ServiceRegistryBuilder();
		srBuilder.applySettings(configuration.getProperties());
		ServiceRegistry serviceRegistry = srBuilder.buildServiceRegistry();
		factory = configuration.buildSessionFactory(serviceRegistry);

		System.out.println("=============");
		saveMessage();

		readMessage();

		factory.close();

	}

	public static void saveMessage() {
		Message message = new Message("Hello, world...");
		Session session = factory.openSession();
		Transaction tx = session.beginTransaction();
		session.persist(message);
		tx.commit();
		session.close();
	}

	public static void readMessage() {
		Session session = factory.openSession();

		List<Message> list = (List<Message>) session
				.createQuery("from Message").list();

		for (Message m : list) {
			System.out.println(m);
		}
		session.close();
	}
}
