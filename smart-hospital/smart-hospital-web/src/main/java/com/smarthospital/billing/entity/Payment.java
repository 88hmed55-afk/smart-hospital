package com.smarthospital.billing.entity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(schema="hospital", name="payments")
public class Payment {
    @Version
    @Column(name = "version", nullable = false)
    private long version;

 @Id @Column(name="id", nullable=false, updatable=false) private UUID id;
 @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="invoice_id", nullable=false, updatable=false) private Invoice invoice;
 @Column(name="receipt_number", nullable=false, unique=true, updatable=false, length=40) private String receiptNumber;
 @Column(name="amount", nullable=false, precision=14, scale=2, updatable=false) private BigDecimal amount;
 @Column(name="currency_code", nullable=false, length=3, updatable=false) private String currencyCode;
 @Enumerated(EnumType.STRING) @Column(name="payment_method", nullable=false, length=20, updatable=false) private PaymentMethod paymentMethod;
 @Enumerated(EnumType.STRING) @Column(name="status", nullable=false, length=16) private PaymentStatus status;
 @Column(name="transaction_reference", length=120, updatable=false) private String transactionReference;
 @Column(name="idempotency_key", length=80, updatable=false) private String idempotencyKey;
 @Column(name="request_fingerprint", length=64, updatable=false) private String requestFingerprint;
 @Column(name="received_by", nullable=false, updatable=false) private UUID receivedBy;
 @Column(name="paid_at", nullable=false, updatable=false) private Instant paidAt;
 protected Payment(){}
 public static Payment post(Invoice invoice,String receipt,BigDecimal amount,PaymentMethod method,String reference,String idempotencyKey,String fingerprint,UUID user){Payment p=new Payment();p.id=UUID.randomUUID();p.invoice=invoice;p.receiptNumber=receipt;p.amount=amount;p.currencyCode=invoice.getCurrencyCode();p.paymentMethod=method;p.status=PaymentStatus.POSTED;p.transactionReference=reference;p.idempotencyKey=idempotencyKey;p.requestFingerprint=fingerprint;p.receivedBy=user;p.paidAt=Instant.now();return p;}
 public UUID getId(){return id;} public UUID getInvoiceId(){return invoice.getId();} public String getIdempotencyKey(){return idempotencyKey;} public String getRequestFingerprint(){return requestFingerprint;} public String getReceiptNumber(){return receiptNumber;} public BigDecimal getAmount(){return amount;} public String getCurrencyCode(){return currencyCode;} public PaymentMethod getPaymentMethod(){return paymentMethod;} public PaymentStatus getStatus(){return status;} public String getTransactionReference(){return transactionReference;} public Instant getPaidAt(){return paidAt;}
}
