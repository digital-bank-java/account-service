package com.digitalbank.accountservice.adapter.in.kafka;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "account.ledger.kafka")
public class LedgerKafkaProperties {

    private boolean enabled;
    private boolean allowInsecureTransport;
    private boolean autoStartup = true;
    private String completedTopic;
    private String failedTopic;
    private String groupId;
    private int retryAttempts;
    private long retryDelayMs;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isAllowInsecureTransport() {
        return allowInsecureTransport;
    }

    public void setAllowInsecureTransport(boolean allowInsecureTransport) {
        this.allowInsecureTransport = allowInsecureTransport;
    }

    public boolean isAutoStartup() {
        return autoStartup;
    }

    public void setAutoStartup(boolean autoStartup) {
        this.autoStartup = autoStartup;
    }

    public String getCompletedTopic() {
        return completedTopic;
    }

    public void setCompletedTopic(String completedTopic) {
        this.completedTopic = completedTopic;
    }

    public String getFailedTopic() {
        return failedTopic;
    }

    public void setFailedTopic(String failedTopic) {
        this.failedTopic = failedTopic;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public int getRetryAttempts() {
        return retryAttempts;
    }

    public void setRetryAttempts(int retryAttempts) {
        this.retryAttempts = retryAttempts;
    }

    public long getRetryDelayMs() {
        return retryDelayMs;
    }

    public void setRetryDelayMs(long retryDelayMs) {
        this.retryDelayMs = retryDelayMs;
    }
}
