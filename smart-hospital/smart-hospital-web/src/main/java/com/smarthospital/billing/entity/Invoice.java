package com.smarthospital.billing.entity;

import com.smarthospital.clinical.entity.Encounter;
import com.smarthospital.patient.entity.Patient;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity @Table(schema="hospital", name="invoices")
public class Invoice {
    @Version
    @Column(name = "version", nullable = false)
    private long version;

 @Id @Column(name="id", nullable=false, updatable=false) private UUID id;
 @Column(name="branch_id", nullable=false, updatable=false) private UUID branchId;
 @Column(name="invoice_number", nullable=false, updatable=false, length=40) private String invoiceNumber;
 @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="patient_id", nullable=false, updatable=false) private Patient patient;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="encounter_id", updatable=false) private Encounter encounter;
 @Enumerated(EnumType.STRING) @Column(name="status", nullable=false, length=20) private InvoiceStatus status;
 @Column(name="currency_code", nullable=false, length=3, updatable=false) private String currencyCode;
 @Column(name="subtotal", nullable=false, precision=14, scale=2) private BigDecimal subtotal;
 @Column(name="discount_amount", nullable=false, precision=14, scale=2) private BigDecimal discountAmount;
 @Column(name="tax_amount", nullable=false, precision=14, scale=2) private BigDecimal taxAmount;
 @Column(name="total_amount", nullable=false, precision=14, scale=2) private BigDecimal totalAmount;
 @Column(name="issued_at") private Instant issuedAt;
 @Column(name="created_by") private UUID createdBy;
 @Column(name="created_at", nullable=false, updatable=false) private Instant createdAt;
 @OneToMany(mappedBy="invoice", cascade={CascadeType.PERSIST}, orphanRemoval=false) @OrderBy("id ASC") private List<InvoiceItem> items=new ArrayList<>();
 protected Invoice(){}
 public static Invoice draft(UUID branchId,String number,Patient patient,Encounter encounter,String currency,UUID userId){Invoice x=new Invoice();x.id=UUID.randomUUID();x.branchId=branchId;x.invoiceNumber=number;x.patient=patient;x.encounter=encounter;x.currencyCode=currency;x.createdBy=userId;x.status=InvoiceStatus.DRAFT;x.subtotal=BigDecimal.ZERO.setScale(2);x.discountAmount=BigDecimal.ZERO.setScale(2);x.taxAmount=BigDecimal.ZERO.setScale(2);x.totalAmount=BigDecimal.ZERO.setScale(2);return x;}
 public void addItem(InvoiceItem item){if(status!=InvoiceStatus.DRAFT)throw new IllegalStateException("Only draft invoices can be changed.");items.add(item);item.attach(this);recalculate();}
 public void recalculate(){subtotal=items.stream().map(InvoiceItem::getLineTotal).reduce(BigDecimal.ZERO.setScale(2),BigDecimal::add).setScale(2);totalAmount=subtotal.subtract(discountAmount).add(taxAmount).setScale(2);}
 public void issue(){if(status!=InvoiceStatus.DRAFT||items.isEmpty())throw new IllegalStateException("A draft invoice with at least one item is required.");recalculate();status=InvoiceStatus.ISSUED;issuedAt=Instant.now();}
 public void applyPayment(BigDecimal postedTotal){if(status==InvoiceStatus.VOID||status==InvoiceStatus.DRAFT)throw new IllegalStateException("Invoice is not payable.");status=postedTotal.compareTo(totalAmount)>=0?InvoiceStatus.PAID:InvoiceStatus.PARTIALLY_PAID;}
 @PrePersist void prePersist(){if(id==null)id=UUID.randomUUID();if(createdAt==null)createdAt=Instant.now();if(status==null)status=InvoiceStatus.DRAFT;}
 public UUID getId(){return id;} public UUID getBranchId(){return branchId;} public String getInvoiceNumber(){return invoiceNumber;} public Patient getPatient(){return patient;} public UUID getPatientId(){return patient.getId();} public String getPatientMrn(){return patient.getMrn();} public String getPatientName(){return patient.getFirstName()+" "+patient.getLastName();} public Encounter getEncounter(){return encounter;} public UUID getEncounterId(){return encounter==null?null:encounter.getId();} public InvoiceStatus getStatus(){return status;} public String getCurrencyCode(){return currencyCode;} public BigDecimal getSubtotal(){return subtotal;} public BigDecimal getDiscountAmount(){return discountAmount;} public BigDecimal getTaxAmount(){return taxAmount;} public BigDecimal getTotalAmount(){return totalAmount;} public Instant getIssuedAt(){return issuedAt;} public Instant getCreatedAt(){return createdAt;} public UUID getCreatedBy(){return createdBy;} public List<InvoiceItem> getItems(){return items;}
}
