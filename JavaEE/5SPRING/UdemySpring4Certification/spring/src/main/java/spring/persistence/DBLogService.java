package spring.persistence;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Transactional 
@Repository //for exception translation
public class DBLogService implements Log{

	private EntityManager entityManager;
	@PersistenceContext //EntityManager injection
	public void setEntityManager(EntityManager entityManager) {
	        this.entityManager = entityManager;
	}
	  
	public boolean log(String log) {
		// TODO Auto-generated method stub
		return false;
	}

	
}

