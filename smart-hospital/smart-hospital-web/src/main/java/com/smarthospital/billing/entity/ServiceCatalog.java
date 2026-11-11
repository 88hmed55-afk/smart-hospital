package com.smarthospital.billing.entity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
@Entity @Table(schema="hospital", name="service_catalog")
public class ServiceCatalog {
 @Id @Column(name="id", nullable=false, updatable=false) private UUID id;
 @Column(name="code", nullable=false, unique=true, length=40) private String code;
 @Column(name="name", nullable=false, length=160) private String name;
 @Column(name="service_type", nullable=false, length=24) private String serviceType;
 @Column(name="standard_price", nullable=false, precision=14, scale=2) private BigDecimal standardPrice;
 @Column(name="currency_code", nullable=false, length=3) private String currencyCode;
 @Column(name="is_active", nullable=false) private boolean active;
 protected ServiceCatalog(){} public UUID getId(){return id;} public String getCode(){return code;} public String getName(){return name;} public String getServiceType(){return serviceType;} public BigDecimal getStandardPrice(){return standardPrice;} public String getCurrencyCode(){return currencyCode;} public boolean isActive(){return active;}
}
