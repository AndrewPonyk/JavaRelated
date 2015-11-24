package ch7;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;

import org.hibernate.Session;

public class ch7DataValidationExample {
	public static void main(String[] args) {
		System.out.println("Data Validation EXAMPLE");

		saveValidatedPerson();
		savePersonWithInvalidDataAndGetException();

		JPASessionUtil.closeAllEntityManagerFactories();
	}

	public static void saveValidatedPerson(){
		EntityManager em = JPASessionUtil.getEntityManager("utiljpa");

		EntityTransaction transaction = em.getTransaction();

		transaction.begin();
		ValidatedSimplePerson v1 = new ValidatedSimplePerson();
		v1.setName("rob");
		v1.setAge(24);
		v1.setLname("mor");
		v1.setEmail("test@gmail.com");
		v1.setMustContains12("12");
		em.persist(v1);
		transaction.commit();

		em.close();
	}

	public static void savePersonWithInvalidDataAndGetException(){
		EntityManager em = JPASessionUtil.getEntityManager("utiljpa");

		try {
			EntityTransaction transaction = em.getTransaction();
			transaction.begin();
			ValidatedSimplePerson v1 = new ValidatedSimplePerson();
			v1.setName("rob");
			v1.setAge(15); // wrong value will get exception during persist
			v1.setLname("mor");
			v1.setEmail("wrongemail@");
			v1.setMustContains12("thisstringdoesntcontains");
			em.persist(v1);
			transaction.commit();

			em.close();
		} catch (Exception e) {
			System.err.println(e.getMessage());
		}

	}


}
