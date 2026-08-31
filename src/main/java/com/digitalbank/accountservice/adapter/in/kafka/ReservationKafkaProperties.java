package com.digitalbank.accountservice.adapter.in.kafka;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "account.reservation.kafka")
public class ReservationKafkaProperties {
	private boolean enabled;
	private boolean allowInsecureTransport;
	private boolean autoStartup = true;
	private String requestedTopic;
	private String releaseRequestedTopic;
	private String acceptedTopic;
	private String rejectedTopic;
	private String releasedTopic;
	private String expiredTopic;
	private String groupId;
	private int retryAttempts = 3;
	private long retryDelayMs = 1000;
	public boolean isEnabled() { return enabled; }
	public void setEnabled(boolean value) { enabled = value; }
	public boolean isAllowInsecureTransport() { return allowInsecureTransport; }
	public void setAllowInsecureTransport(boolean value) { allowInsecureTransport = value; }
	public boolean isAutoStartup() { return autoStartup; }
	public void setAutoStartup(boolean value) { autoStartup = value; }
	public String getRequestedTopic() { return requestedTopic; }
	public void setRequestedTopic(String value) { requestedTopic = value; }
	public String getReleaseRequestedTopic() { return releaseRequestedTopic; }
	public void setReleaseRequestedTopic(String value) { releaseRequestedTopic = value; }
	public String getAcceptedTopic() { return acceptedTopic; }
	public void setAcceptedTopic(String value) { acceptedTopic = value; }
	public String getRejectedTopic() { return rejectedTopic; }
	public void setRejectedTopic(String value) { rejectedTopic = value; }
	public String getReleasedTopic() { return releasedTopic; }
	public void setReleasedTopic(String value) { releasedTopic = value; }
	public String getExpiredTopic() { return expiredTopic; }
	public void setExpiredTopic(String value) { expiredTopic = value; }
	public String getGroupId() { return groupId; }
	public void setGroupId(String value) { groupId = value; }
	public int getRetryAttempts() { return retryAttempts; }
	public void setRetryAttempts(int value) { retryAttempts = value; }
	public long getRetryDelayMs() { return retryDelayMs; }
	public void setRetryDelayMs(long value) { retryDelayMs = value; }
}
