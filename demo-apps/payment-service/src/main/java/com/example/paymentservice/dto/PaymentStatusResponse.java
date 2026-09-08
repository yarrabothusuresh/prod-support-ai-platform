package com.example.paymentservice.dto;

public class PaymentStatusResponse {

    private String service;
    private String message;

    public PaymentStatusResponse() {
    }

    public PaymentStatusResponse(String service, String message) {
        this.service = service;
        this.message = message;
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
