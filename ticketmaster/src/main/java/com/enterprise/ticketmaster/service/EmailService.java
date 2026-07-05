package com.enterprise.ticketmaster.service;

import com.enterprise.ticketmaster.model.Ticket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "spring.mail.host")
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String fromAddress;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Async
    public void sendTicketReceived(String toEmail, Long ticketId, String title) {
        String subject = "We've received your request — Ticket #" + ticketId;
        String body = """
                <div style="font-family: Inter, sans-serif; max-width: 600px; margin: 0 auto;">
                    <div style="background: #1A73E8; padding: 24px 32px;">
                        <h1 style="color: white; margin: 0; font-size: 20px;">Ticketmaster Enterprise</h1>
                    </div>
                    <div style="padding: 32px; border: 1px solid #E0E0E0; border-top: none;">
                        <h2 style="color: #202124; margin: 0 0 16px 0;">Your Request Has Been Received</h2>
                        <p style="color: #5F6368;">Thanks for reaching out. Ticket <strong>#%d</strong> has been logged and a member of our team will be with you shortly.</p>
                        <div style="background: #F8F9FA; padding: 16px; border-radius: 8px; margin: 24px 0;">
                            <p style="margin: 0; font-weight: 600; color: #202124;">%s</p>
                        </div>
                        <p style="color: #5F6368;">You can check on the status of your request at any time by logging in to the portal.</p>
                    </div>
                </div>
                """.formatted(ticketId, title);
        send(toEmail, subject, body);
    }

    @Async
    public void sendTicketCreated(String toEmail, Long ticketId, String title) {
        String subject = "New Ticket #" + ticketId + " Raised: " + title;
        String body = """
                <div style="font-family: Inter, sans-serif; max-width: 600px; margin: 0 auto;">
                    <div style="background: #1A73E8; padding: 24px 32px;">
                        <h1 style="color: white; margin: 0; font-size: 20px;">Ticketmaster Enterprise</h1>
                    </div>
                    <div style="padding: 32px; border: 1px solid #E0E0E0; border-top: none;">
                        <h2 style="color: #202124; margin: 0 0 16px 0;">New Incident Raised</h2>
                        <p style="color: #5F6368;">Ticket <strong>#%d</strong> has been created and assigned to your queue.</p>
                        <div style="background: #F8F9FA; padding: 16px; border-radius: 8px; margin: 24px 0;">
                            <p style="margin: 0; font-weight: 600; color: #202124;">%s</p>
                        </div>
                        <p style="color: #5F6368;">Log in to your workspace to review and acknowledge this ticket.</p>
                    </div>
                </div>
                """.formatted(ticketId, title);
        send(toEmail, subject, body);
    }

    @Async
    public void sendSlaBreached(String toEmail, Long ticketId, String title) {
        String subject = "🚨 SLA Breached — Ticket #" + ticketId;
        String body = """
                <div style="font-family: Inter, sans-serif; max-width: 600px; margin: 0 auto;">
                    <div style="background: #D93025; padding: 24px 32px;">
                        <h1 style="color: white; margin: 0; font-size: 20px;">Ticketmaster Enterprise</h1>
                    </div>
                    <div style="padding: 32px; border: 1px solid #E0E0E0; border-top: none;">
                        <h2 style="color: #D93025; margin: 0 0 16px 0;">SLA Breach Alert</h2>
                        <p style="color: #5F6368;">Ticket <strong>#%d</strong> has exceeded its SLA deadline and requires immediate attention.</p>
                        <div style="background: #FCE8E6; padding: 16px; border-radius: 8px; margin: 24px 0; border-left: 4px solid #D93025;">
                            <p style="margin: 0; font-weight: 600; color: #D93025;">%s</p>
                        </div>
                        <p style="color: #5F6368;">Please log in immediately and escalate this ticket.</p>
                    </div>
                </div>
                """.formatted(ticketId, title);
        send(toEmail, subject, body);
    }

    @Async
    public void sendSlaWarning(String toEmail, Long ticketId, String title) {
        String subject = "⚠️ SLA Warning — Ticket #" + ticketId + " approaching deadline";
        String body = """
            <div style="font-family: Inter, sans-serif; max-width: 600px; margin: 0 auto;">
                <div style="background: #F9AB00; padding: 24px 32px;">
                    <h1 style="color: white; margin: 0; font-size: 20px;">Ticketmaster Enterprise</h1>
                </div>
                <div style="padding: 32px; border: 1px solid #E0E0E0; border-top: none;">
                    <h2 style="color: #F9AB00; margin: 0 0 16px 0;">SLA Warning</h2>
                    <p style="color: #5F6368;">Ticket <strong>#%d</strong> has used 75%% of its SLA time. Only 25%% remains.</p>
                    <div style="background: #FEF7E0; padding: 16px; border-radius: 8px; margin: 24px 0; border-left: 4px solid #F9AB00;">
                        <p style="margin: 0; font-weight: 600; color: #B06000;">%s</p>
                    </div>
                    <p style="color: #5F6368;">Act now to avoid an SLA breach.</p>
                </div>
            </div>
            """.formatted(ticketId, title);
        send(toEmail, subject, body);
    }

    @Async
    public void sendSlaCritical(String toEmail, Long ticketId, String title) {
        String subject = "🔴 SLA Critical — Ticket #" + ticketId + " — 10% time remaining";
        String body = """
            <div style="font-family: Inter, sans-serif; max-width: 600px; margin: 0 auto;">
                <div style="background: #D93025; padding: 24px 32px;">
                    <h1 style="color: white; margin: 0; font-size: 20px;">Ticketmaster Enterprise</h1>
                </div>
                <div style="padding: 32px; border: 1px solid #E0E0E0; border-top: none;">
                    <h2 style="color: #D93025; margin: 0 0 16px 0;">SLA Critical Alert</h2>
                    <p style="color: #5F6368;">Ticket <strong>#%d</strong> has only 10%% of SLA time remaining. Immediate action required.</p>
                    <div style="background: #FCE8E6; padding: 16px; border-radius: 8px; margin: 24px 0; border-left: 4px solid #D93025;">
                        <p style="margin: 0; font-weight: 600; color: #D93025;">%s</p>
                    </div>
                    <p style="color: #5F6368;">Resolve or escalate this ticket immediately to prevent SLA breach.</p>
                </div>
            </div>
            """.formatted(ticketId, title);
        send(toEmail, subject, body);
    }

    @org.springframework.beans.factory.annotation.Value("${app.base-url}")
    private String baseUrl;

    @Async
    public void sendTicketResolved(String toEmail, Long ticketId, String title, String resolvedBy, String csatToken) {
        String subject = "✅ Ticket #" + ticketId + " Resolved";
        String starLinks = java.util.stream.IntStream.rangeClosed(1, 5)
                .mapToObj(n -> "<a href=\"%s/api/csat/rate?ticketId=%d&token=%s&rating=%d\" style=\"text-decoration:none; font-size:28px; margin:0 4px;\">%s</a>"
                        .formatted(baseUrl, ticketId, csatToken, n, n <= 5 ? "⭐".repeat(1) : ""))
                .collect(java.util.stream.Collectors.joining());
        String body = """
            <div style="font-family: Inter, sans-serif; max-width: 600px; margin: 0 auto;">
                <div style="background: #1E8E3E; padding: 24px 32px;">
                    <h1 style="color: white; margin: 0; font-size: 20px;">Ticketmaster Enterprise</h1>
                </div>
                <div style="padding: 32px; border: 1px solid #E0E0E0; border-top: none;">
                    <h2 style="color: #1E8E3E; margin: 0 0 16px 0;">Incident Resolved</h2>
                    <p style="color: #5F6368;">Ticket <strong>#%d</strong> has been marked as resolved by <strong>%s</strong>.</p>
                    <div style="background: #E6F4EA; padding: 16px; border-radius: 8px; margin: 24px 0; border-left: 4px solid #1E8E3E;">
                        <p style="margin: 0; font-weight: 600; color: #1E8E3E;">%s</p>
                    </div>
                    <p style="color: #5F6368; text-align:center; margin-top:28px;">How did we do?</p>
                    <div style="text-align:center;">%s</div>
                    <p style="color: #5F6368;">The ticket will be automatically closed after 3 days if no further action is required.</p>
                </div>
            </div>
            """.formatted(ticketId, resolvedBy != null ? resolvedBy : "System", title, starLinks);
        send(toEmail, subject, body);
    }

    @Async
    public void sendTicketAssignmentEmail(String toEmail, Ticket ticket) {
        String subject = "New Ticket Assigned: #" + ticket.getId() + " - " + ticket.getTitle();
        String body = """
                <div style="font-family: Inter, sans-serif; max-width: 600px; margin: 0 auto;">
                    <div style="background: #1A73E8; padding: 24px 32px;">
                        <h1 style="color: white; margin: 0; font-size: 20px;">Ticketmaster Enterprise</h1>
                    </div>
                    <div style="padding: 32px; border: 1px solid #E0E0E0; border-top: none;">
                        <h2 style="color: #202124; margin: 0 0 16px 0;">Ticket Assigned To You</h2>
                        <p style="color: #5F6368;">Ticket <strong>#%d</strong> has just been routed to your queue.</p>
                        <div style="background: #F8F9FA; padding: 16px; border-radius: 8px; margin: 24px 0; border-left: 4px solid #1A73E8;">
                            <p style="margin: 0; font-weight: 600; color: #202124;">%s</p>
                            <p style="margin: 8px 0 0 0; font-size: 14px; color: #5F6368;">Priority: <strong>%s</strong></p>
                        </div>
                        <p style="color: #5F6368;">Please log in to the Enterprise Console to acknowledge and begin work on this request.</p>
                    </div>
                </div>
                """.formatted(ticket.getId(), ticket.getTitle(), ticket.getPriority());

        send(toEmail, subject, body);
    }

    private void send(String to, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("Email sent to {} — subject: {}", to, subject);
        } catch (MessagingException e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
        }
    }

    @Async
    public void sendEscalation(String toEmail, Long ticketId, String title, String newPriority) {
        String subject = "🚨 Escalated — Ticket #" + ticketId + " breached SLA";
        String body = """
            <div style="font-family: Inter, sans-serif; max-width: 600px; margin: 0 auto;">
                <div style="background: #D93025; padding: 24px 32px;">
                    <h1 style="color: white; margin: 0; font-size: 20px;">Ticketmaster Enterprise</h1>
                </div>
                <div style="padding: 32px; border: 1px solid #E0E0E0; border-top: none;">
                    <h2 style="color: #D93025; margin: 0 0 16px 0;">Ticket Escalated To You</h2>
                    <p style="color: #5F6368;">Ticket <strong>#%d</strong> breached its SLA and has been escalated to you. Priority is now <strong>%s</strong>.</p>
                    <div style="background: #FCE8E6; padding: 16px; border-radius: 8px; margin: 24px 0; border-left: 4px solid #D93025;">
                        <p style="margin: 0; font-weight: 600; color: #D93025;">%s</p>
                    </div>
                    <p style="color: #5F6368;">Please review this ticket as soon as possible.</p>
                </div>
            </div>
            """.formatted(ticketId, newPriority, title);
        send(toEmail, subject, body);
    }
}