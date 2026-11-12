package com.epictalenttrust.hr.mail;

import com.epictalenttrust.hr.config.EttProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.mail.autoconfigure.MailProperties;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Sends email over SMTP when SMTP_HOST is set; otherwise writes the email to the log (development). */
@Component
public class MailGateway {

    private static final Logger log = LoggerFactory.getLogger(MailGateway.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final MailProperties mailProperties;
    private final EttProperties properties;

    public MailGateway(ObjectProvider<JavaMailSender> mailSender, MailProperties mailProperties, EttProperties properties) {
        this.mailSender = mailSender;
        this.mailProperties = mailProperties;
        this.properties = properties;
    }

    public String providerName() {
        return isSmtpConfigured() ? "smtp:" + mailProperties.getHost() : "log";
    }

    public void send(String to, String subject, String text, String html) throws MessagingException {
        if (!isSmtpConfigured()) {
            log.info("Email (not sent, SMTP_HOST not set) to={} subject=\"{}\"\n{}", to, subject, text);
            return;
        }
        JavaMailSender sender = mailSender.getObject();
        MimeMessage message = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(properties.mail().from());
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(text, html);
        sender.send(message);
    }

    private boolean isSmtpConfigured() {
        return StringUtils.hasText(mailProperties.getHost());
    }
}
