package spring.jdbc.transactions.orm;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import spring.jdbc.transactions.orm.User;
import spring.jdbc.transactions.orm.UserManager;

public class UserManagerTransactionTemplate {

	private TransactionTemplate transactionTemplate;
	private UserManager userManager;

	public UserManagerTransactionTemplate(PlatformTransactionManager transactionManager) {
	    this.transactionTemplate = new TransactionTemplate(transactionManager);
	    transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
	    transactionTemplate.setTimeout(30);
	}
	public void doItInTransactionUserManager() {
		
		transactionTemplate.execute(new TransactionCallbackWithoutResult() {			
			@Override
			protected void doInTransactionWithoutResult(TransactionStatus status) {
				System.out.println("status.isNewTransaction(): " + status.isNewTransaction());
				User user = new User();
				user.setUsername("testTransactionTemplate");
				user.setPassword("111");
				user.setActive(true);
				
				userManager.addUSER(user);
				System.out.println("status.isCompleted(): " + status.isCompleted());
				userManager.logAllUserInfo();
				
			}
		});
	}
	public UserManager getUserManager() {
		return userManager;
	}
	public void setUserManager(UserManager userManager) {
		this.userManager = userManager;
	}
}
