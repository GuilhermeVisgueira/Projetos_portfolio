package com.visgueira.ecommerce.service;

import com.visgueira.ecommerce.model.Order;
import com.visgueira.ecommerce.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailVerificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);

    private final JavaMailSender mailSender;
    private final String verificationBaseUrl;

    public EmailVerificationService(
            JavaMailSender mailSender,
            @Value("${app.security.email-verification.base-url}") String verificationBaseUrl) {
        this.mailSender = mailSender;
        this.verificationBaseUrl = verificationBaseUrl;
    }

    public void sendAccountVerificationEmail(User user, String token) {
        String accountType = "SELLER".equalsIgnoreCase(user.getRole())
            ? "Vendedor (Loja: " + user.getStoreName() + ")"
            : "Consumidor";

        String verifyLink = verificationBaseUrl + "?email=" + user.getEmail() + "&token=" + token;

        String subject = "[E-commerce] Confirme seu e-mail - Código: " + token;
        String body = """
            Olá, %s!
            
            Sua conta de %s foi criada no E-commerce.
            Para ativar sua conta, utilize o código de verificação abaixo ou clique no link:
            
            Código de Verificação (OTP): %s
            Link de Confirmação Direta: %s
            
            (Este código expira em 30 minutos).
            """.formatted(user.getName(), accountType, token, verifyLink);

        sendOrLogEmail(user.getEmail(), subject, body);
    }

    public void sendOrderPaidConfirmationEmail(Order order) {
        String subject = "[E-commerce] Pagamento Confirmado - Pedido #" + order.getId();
        String body = """
            Olá!
            
            O pagamento do seu pedido #%s foi autenticado e confirmado via Stripe!
            Valor Total: R$ %.2f
            Sessão Stripe: %s
            Status: %s
            """.formatted(
                order.getId(),
                order.getTotalAmountCents() / 100.0,
                order.getStripeSessionId(),
                order.getStatus()
            );

        sendOrLogEmail(order.getCustomerEmail(), subject, body);
    }

    private void sendOrLogEmail(String to, String subject, String text) {
        log.info("==================================================");
        log.info("DISPARO DE E-MAIL -> Para: {} | Assunto: {}", to, subject);
        log.info("Conteúdo:\n{}", text.trim());
        log.info("==================================================");

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("noreply@ecommerce.local");
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
            log.info("E-mail entregue ao servidor SMTP para {}", to);
        } catch (Exception e) {
            log.warn("Servidor SMTP indisponível no momento ({}). O código de verificação está visível no log acima.", e.getMessage());
        }
    }
}
