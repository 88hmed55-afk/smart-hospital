package com.smarthospital.billing.dto;

import com.smarthospital.billing.entity.ServiceCatalog;
import java.math.BigDecimal;
import java.util.UUID;

public class ServiceCatalogResponse {
    public UUID id;
    public String code;
    public String name;
    public String serviceType;
    public BigDecimal standardPrice;
    public String currencyCode;

    public static ServiceCatalogResponse from(ServiceCatalog service) {
        ServiceCatalogResponse response = new ServiceCatalogResponse();
        response.id = service.getId();
        response.code = service.getCode();
        response.name = service.getName();
        response.serviceType = service.getServiceType();
        response.standardPrice = service.getStandardPrice();
        response.currencyCode = service.getCurrencyCode();
        return response;
    }
}
