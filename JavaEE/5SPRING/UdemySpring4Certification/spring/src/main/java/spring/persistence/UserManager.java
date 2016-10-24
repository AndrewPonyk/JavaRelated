package spring.persistence;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import javax.persistence.TypedQuery;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository //for exception translation
@Transactional
public class UserManager {

	@PersistenceContext	
	private EntityManager entityManager;
			
	public List<User> queryfindAllUsersJPA() {	
		
		System.out.println("UserManager queryfindAllUsersJPA is called");
		String qlString = "from User";
		TypedQuery<User> query = entityManager.createQuery(qlString, User.class);			 
		return query.getResultList();
	}
	
	public int queryCountAllUsersJPA() {	
		
		System.out.println("UserManager queryCountAllUsersJPA is called");
		String qlString = "from User";
		TypedQuery<User> query = entityManager.createQuery(qlString, User.class);			 
		return query.getResultList().size();
	}
	
	public User queryFindByIdUser(int idUser) {	
		System.out.println("UserManager queryFindByIdUser is called");
		return entityManager.find(User.class,idUser);
	}
	
	@Transactional
	public boolean insertUserByIdUser(String username, String password, boolean active) {
		
		System.out.println("UserManager queryFindByIdUser is called");
		String qlString = "insert into user (username,password,active) values (?,?,?)";
		entityManager.joinTransaction();;
		Query query = entityManager.createNativeQuery(qlString);		
		query.setParameter(1, username);
		query.setParameter(2, password);
		query.setParameter(3, active);
		int result = query.executeUpdate();
		if(result>0)
		return true;
		else
		return false;		
	}

	@Transactional
	public boolean deleteUserByIdUser(int idUser) {
		
		System.out.println("UserManager insertUserByIdUser is called");
		String qlString = "delete from user where iduser=?";
		entityManager.joinTransaction();
		Query query = entityManager.createNativeQuery(qlString);		
		query.setParameter(1, idUser);
		int result = query.executeUpdate();
		if(result>0)
		return true;
		else
		return false;		
	}
}
