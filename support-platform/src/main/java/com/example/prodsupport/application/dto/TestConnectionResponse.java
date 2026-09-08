package com.example.prodsupport.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TestConnectionResponse {

    private boolean connected;
    private String applicationName;
    private String status;
    private Long responseTimeMs;
    private String error;

    public TestConnectionResponse() {
    }

    public static TestConnectionResponse success(String applicationName, String status, long responseTimeMs) {
        TestConnectionResponse res = new TestConnectionResponse();
        res.setConnected(true);
        res.setApplicationName(applicationName);
        res.setStatus(status);
        res.setResponseTimeMs(responseTimeMs);
        return res;
    }

    public static TestConnectionResponse failure(String error) {
        TestConnectionResponse res = new TestConnectionResponse();
        res.setConnected(false);
        res.setError(error);
        return res;
    }

    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getResponseTimeMs() {
        return responseTimeMs;
    }

    public void setResponseTimeMs(Long responseTimeMs) {
        this.responseTimeMs = responseTimeMs;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
