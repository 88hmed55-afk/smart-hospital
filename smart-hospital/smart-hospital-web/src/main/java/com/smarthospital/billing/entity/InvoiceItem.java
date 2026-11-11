package com.smarthospital.billing.entity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
@Entity @Table(schema="hospital", name="invoice_items")
public class InvoiceItem {
 @Id @Column(name="id", nullable=false, updatable=false) private UUID id;
 @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="invoice_id", nullable=false, updatable=false) private Invoice invoice;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="service_id") private ServiceCatalog service;
 @Column(name="description_snapshot", nullable=false, length=240, updatable=false) private String descriptionSnapshot;
 @Column(name="quantity", nullable=false, precision=14, scale=3, updatable=false) private BigDecimal quantity;
 @Column(name="unit_price", nullable=false, precision=14, scale=2, updatable=false) private BigDecimal unitPrice;
 @Column(name="line_discount", nullable=false, precision=14, scale=2, updatable=false) private BigDecimal lineDiscount;
 @Column(name="line_total", nullable=false, precision=14, scale=2, updatable=false) private BigDecimal lineTotal;
 protected InvoiceItem(){}
 public static InvoiceItem service(ServiceCatalog s,BigDecimal q){InvoiceItem i=new InvoiceItem();i.id=UUID.randomUUID();i.service=s;i.descriptionSnapshot=s.getName();i.quantity=q;i.unitPrice=s.getStandardPrice();i.lineDiscount=BigDecimal.ZERO.setScale(2);i.lineTotal=q.multiply(i.unitPrice).setScale(2,java.math.RoundingMode.HALF_UP);return i;}
 void attach(Invoice invoice){this.invoice=invoice;}
 public UUID getId(){return id;} public ServiceCatalog getService(){return service;} public String getDescriptionSnapshot(){return descriptionSnapshot;} public BigDecimal getQuantity(){return quantity;} public BigDecimal getUnitPrice(){return unitPrice;} public BigDecimal getLineDiscount(){return lineDiscount;} public BigDecimal getLineTotal(){return lineTotal;}
}
