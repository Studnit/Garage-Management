package com.citycarservices.garage.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Business information and tunables, bound from the "garage.*" properties. */
@ConfigurationProperties(prefix = "garage")
@Getter
@Setter
public class GarageProperties {

    private String name = "City Car Services";
    private String tagline = "Multi-brand car repair & service centre";
    private String address = "";
    private String phone = "";
    private String phoneDigits = "";
    private String email = "";
    private String hoursText = "";
    private int openHour = 9;
    private int closeHour = 21;
    private String gstin = "";
    private String pan = "";
    private String stateName = "Maharashtra";
    private String stateCode = "27";
    private String upiId = "";
    private String bankDetails = "";
    private String mapUrl = "";
    private String uploadDir = "./data/uploads";
    private boolean seedDemo = true;
    private String adminEmail = "admin@citycarservices.in";
    private String adminPassword = "";
    private int reminderLeadDays = 30;
    private int reminderKmLead = 500;
    private String reminderCron = "0 0 9 * * *";
    private String notificationWebhookUrl = "";
    private String mailFrom = "";
    private String invoiceTerms = "";

    public String getWhatsappLink() {
        return "https://wa.me/" + phoneDigits;
    }

    public String getPhoneLink() {
        return "tel:+" + phoneDigits;
    }
}
