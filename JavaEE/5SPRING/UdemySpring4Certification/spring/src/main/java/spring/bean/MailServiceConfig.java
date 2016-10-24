package spring.bean;

public class MailServiceConfig {
	private boolean active;
	private String maxHourlyEmailLimit;	

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public String getMaxHourlyEmailLimit() {
		return maxHourlyEmailLimit;
	}

	public void setMaxHourlyEmailLimit(String maxHourlyEmailLimit) {
		this.maxHourlyEmailLimit = maxHourlyEmailLimit;
	}

	
	
	
}
