package com.shrirang.distributed_promptforge.account_service.service.impl;

import com.shrirang.distributed_promptforge.account_service.service.EmailService;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String smtpUsername;

    @Value("${spring.mail.host}")
    private String smtpHost;

    /**
     * Sends an HTML email synchronously so that any SMTP failure
     * immediately propagates as a RuntimeException to the caller.
     * The caller (AuthServiceImpl) is responsible for deciding
     * whether to expose the error to the user.
     */
    @Override
    public void sendHtmlEmail(String to, String subject, String body) {
        String cleanTo = to.trim().toLowerCase();
        log.info("Sending email to {} | subject: {}", cleanTo, subject);
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(cleanTo);
            helper.setSubject(subject);
            helper.setText(body, true);
            // From must match the authenticated SMTP username for Gmail
            helper.setFrom(smtpUsername, "PromptForge");
            mailSender.send(message);
            log.info("Email sent successfully to {}", cleanTo);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", cleanTo, e.getMessage(), e);
            throw new RuntimeException("Failed to send email to " + cleanTo + ": " + e.getMessage(), e);
        }
    }

    /**
     * Step 1 — Regex syntax check (fast, no network).
     * Step 2 — SMTP RCPT TO probe: opens a connection to Gmail SMTP,
     *           does EHLO + MAIL FROM + RCPT TO, reads the 250/550 response,
     *           then immediately sends RSET + QUIT without actually sending mail.
     *
     * Why SMTP probe instead of MX lookup:
     *   MX lookup only tells us the domain has a mail server.
     *   RCPT TO tells us whether the *specific mailbox* exists.
     *   Gmail replies 550 5.1.1 for non-existent addresses before accepting the message,
     *   so we catch fake/typo emails before wasting a send.
     *
     * Limitations: some providers (Outlook, Yahoo) accept all addresses at RCPT TO
     *   and bounce later. For those, the probe returns true (safe — we still send).
     */
    @Override
    public boolean isEmailValid(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        String cleanEmail = email.trim().toLowerCase();

        // ── Step 1: syntax check ──────────────────────────────────────────────
        boolean syntaxOk = cleanEmail.matches(
                "^[a-zA-Z0-9_+&*\\-]+(?:\\.[a-zA-Z0-9_+&*\\-]+)*@(?:[a-zA-Z0-9\\-]+\\.)+[a-zA-Z]{2,}$"
        );
        if (!syntaxOk) {
            log.debug("Email failed syntax check: {}", cleanEmail);
            return false;
        }

        // ── Step 2: SMTP RCPT TO probe ────────────────────────────────────────
        try {
            boolean exists = smtpRcptToProbe(cleanEmail);
            if (!exists) {
                log.warn("SMTP probe: mailbox does not exist — {}", cleanEmail);
            }
            return exists;
        } catch (Exception e) {
            // Network / timeout — fail open (don't block legitimate users)
            log.debug("SMTP probe failed for {} ({}), failing open", cleanEmail, e.getMessage());
            return true;
        }
    }

    /**
     * Opens a plain SMTP connection to port 25 of the recipient's MX host
     * (or falls back to smtp.gmail.com for gmail.com addresses),
     * performs EHLO → MAIL FROM → RCPT TO, captures the response code,
     * then tears down cleanly with RSET + QUIT.
     *
     * Returns true  if the server replies 250 (mailbox exists).
     * Returns false if the server replies 550 / 551 / 553 (mailbox not found).
     * Throws        on any network / timeout error (caller should fail-open).
     */
    private boolean smtpRcptToProbe(String email) throws Exception {
        String domain = email.substring(email.indexOf('@') + 1);

        // Resolve MX record via DNS — use Java's built-in DNS
        String mxHost = resolveMx(domain);
        log.debug("SMTP probe: domain={} mx={} email={}", domain, mxHost, email);

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(mxHost, 25), 5000);
            socket.setSoTimeout(5000);

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));
            PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);

            // Read banner
            readResponse(reader);

            // EHLO
            writer.println("EHLO promptforge.app");
            readResponse(reader);

            // MAIL FROM — use our sending address
            writer.println("MAIL FROM:<" + smtpUsername + ">");
            readResponse(reader);

            // RCPT TO — this is the key check
            writer.println("RCPT TO:<" + email + ">");
            String rcptResponse = readResponse(reader);

            // Clean up — don't actually send anything
            writer.println("RSET");
            readResponse(reader);
            writer.println("QUIT");

            // 250 = OK (mailbox exists)
            // 550, 551, 553 = mailbox not found
            int code = parseCode(rcptResponse);
            log.debug("SMTP probe RCPT TO response for {}: {} -> code {}", email, rcptResponse.trim(), code);
            return code == 250 || code == 251; // 251 = will forward
        }
    }

    /**
     * Resolve the MX hostname for a domain using nslookup via DNS Java API.
     * Falls back to the domain itself if no MX record found.
     */
    private String resolveMx(String domain) {
        try {
            // Use Java's JNDI DNS lookup for MX records
            javax.naming.directory.InitialDirContext idc =
                    new javax.naming.directory.InitialDirContext();
            javax.naming.directory.Attributes attrs =
                    idc.getAttributes("dns:/" + domain, new String[]{"MX"});
            javax.naming.directory.Attribute mxAttr = attrs.get("MX");

            if (mxAttr == null || mxAttr.size() == 0) {
                return domain; // fallback
            }

            // MX records format: "priority hostname" — pick lowest priority (highest preference)
            String best = null;
            int bestPriority = Integer.MAX_VALUE;
            for (int i = 0; i < mxAttr.size(); i++) {
                String record = mxAttr.get(i).toString().trim();
                String[] parts = record.split("\\s+");
                if (parts.length >= 2) {
                    int priority = Integer.parseInt(parts[0]);
                    String host = parts[1].replaceAll("\\.$", ""); // strip trailing dot
                    if (priority < bestPriority) {
                        bestPriority = priority;
                        best = host;
                    }
                }
            }
            return best != null ? best : domain;
        } catch (Exception e) {
            log.debug("MX lookup failed for {}: {}", domain, e.getMessage());
            return domain; // fallback to domain itself
        }
    }

    /** Read a (possibly multi-line) SMTP response and return the last line. */
    private String readResponse(BufferedReader reader) throws Exception {
        String line;
        String last = "";
        while ((line = reader.readLine()) != null) {
            last = line;
            // Multi-line responses have a '-' after the code; final line has a space
            if (line.length() >= 4 && line.charAt(3) == ' ') break;
            if (line.length() < 4) break;
        }
        return last;
    }

    /** Parse the 3-digit SMTP response code from a response line. */
    private int parseCode(String response) {
        if (response == null || response.length() < 3) return 0;
        try {
            return Integer.parseInt(response.substring(0, 3));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
