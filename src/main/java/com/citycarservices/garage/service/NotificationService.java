package com.citycarservices.garage.service;

import com.citycarservices.garage.config.GarageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Sends customer notifications.
 * Email goes through Spring Mail when spring.mail.host and garage.mail-from are configured.
 * WhatsApp / SMS go through a configurable webhook (garage.notification-webhook-url) which receives JSON:
 * {"to":"+91XXXXXXXXXX","email":"...","subject":"...","message":"..."} so any gateway (WhatsApp Cloud API,
 * Twilio, MSG91, ...) can be bridged without code changes. Without configuration the message is only logged.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    public record Result(boolean delivered, boolean attempted, String detail) {
    }

    private final GarageProperties props;
    private final ObjectProvider<JavaMailSender> mailSender;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public NotificationService(GarageProperties props, ObjectProvider<JavaMailSender> mailSender) {
        this.props = props;
        this.mailSender = mailSender;
    }

    @Async
    public void notifyAsync(String mobile, String email, String subject, String message) {
        send(mobile, email, subject, message);
    }

    public Result send(String mobile, String email, String subject, String message) {
        List<String> ok = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender != null && email != null && !email.isBlank() && !props.getMailFrom().isBlank()) {
            try {
                SimpleMailMessage m = new SimpleMailMessage();
                m.setFrom(props.getMailFrom());
                m.setTo(email);
                m.setSubject(subject);
                m.setText(message + "\n\n" + props.getName() + "\n" + props.getPhone());
                sender.send(m);
                ok.add("Email");
            } catch (Exception e) {
                log.warn("Email to {} failed: {}", email, e.getMessage());
                failed.add("Email failed");
            }
        }

        String url = props.getNotificationWebhookUrl();
        if (url != null && !url.isBlank() && mobile != null) {
            try {
                String body = "{\"to\":\"+91" + esc(mobile) + "\",\"email\":\"" + esc(email) + "\",\"subject\":\""
                        + esc(subject) + "\",\"message\":\"" + esc(message) + "\"}";
                HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() / 100 == 2) {
                    ok.add("WhatsApp/SMS gateway");
                } else {
                    failed.add("Gateway returned HTTP " + resp.statusCode());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                failed.add("Gateway interrupted");
            } catch (Exception e) {
                log.warn("Webhook notification failed: {}", e.getMessage());
                failed.add("Gateway unreachable");
            }
        }

        if (ok.isEmpty() && failed.isEmpty()) {
            log.info("[notification not sent - no channel configured] to={} subject={}", mobile, subject);
            return new Result(false, false, "No delivery channel configured");
        }
        String detail = String.join("; ", ok.isEmpty() ? failed : ok);
        return new Result(!ok.isEmpty(), true, detail);
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(' ');
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
