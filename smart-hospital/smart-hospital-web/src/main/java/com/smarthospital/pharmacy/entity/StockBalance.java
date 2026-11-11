package com.smarthospital.pharmacy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "hospital", name = "stock_balances")
public class StockBalance {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private InventoryLocation location;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private MedicationBatch batch;

    @Column(name = "quantity_on_hand", nullable = false, precision = 14, scale = 3)
    private BigDecimal quantityOnHand;

    @Column(name = "quantity_reserved", nullable = false, precision = 14, scale = 3)
    private BigDecimal quantityReserved;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StockBalance() { }

    public static StockBalance received(InventoryLocation location, MedicationBatch batch, BigDecimal quantity) {
        StockBalance balance = new StockBalance();
        balance.id = UUID.randomUUID();
        balance.location = location;
        balance.batch = batch;
        balance.quantityOnHand = quantity;
        balance.quantityReserved = BigDecimal.ZERO.setScale(3);
        balance.updatedAt = Instant.now();
        return balance;
    }

    public void receive(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) throw new IllegalArgumentException("Receipt quantity must be positive.");
        quantityOnHand = quantityOnHand.add(quantity);
        updatedAt = Instant.now();
    }

    public BigDecimal getAvailableQuantity() {
        return quantityOnHand.subtract(quantityReserved);
    }

    public void dispense(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0 || getAvailableQuantity().compareTo(quantity) < 0) {
            throw new IllegalStateException("The requested quantity is not available in this stock batch.");
        }
        quantityOnHand = quantityOnHand.subtract(quantity);
        updatedAt = Instant.now();
    }

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (quantityOnHand == null) quantityOnHand = BigDecimal.ZERO;
        if (quantityReserved == null) quantityReserved = BigDecimal.ZERO;
        if (updatedAt == null) updatedAt = Instant.now();
    }

    @PreUpdate
    void preUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public InventoryLocation getLocation() { return location; }
    public MedicationBatch getBatch() { return batch; }
    public BigDecimal getQuantityOnHand() { return quantityOnHand; }
    public BigDecimal getQuantityReserved() { return quantityReserved; }
    public long getVersion() { return version; }
}
