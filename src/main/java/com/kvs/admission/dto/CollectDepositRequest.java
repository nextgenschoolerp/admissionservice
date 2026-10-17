package com.kvs.admission.dto;

import com.kvs.admission.enums.PaymentMode;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request DTO for collecting a seat reservation deposit against an offer.
 * US-02008: Collect seat reservation deposit fees and issue digital receipts
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CollectDepositRequest {

    @NotNull(message = "Amount paid is required")
    @DecimalMin(value = "0.01", message = "Amount paid must be greater than zero")
    @Digits(integer = 8, fraction = 2, message = "Amount paid must have at most 8 digits and 2 decimals")
    private BigDecimal amountPaid;

    @NotNull(message = "Payment mode is required")
    private PaymentMode paymentMode;

    /** Mandatory for every payment mode except CASH. */
    @Size(max = 100, message = "Transaction reference must not exceed 100 characters")
    @Pattern(regexp = "^[A-Za-z0-9/_-]*$", message = "Transaction reference contains invalid characters")
    private String transactionReference;

    @Size(max = 500, message = "Remarks must not exceed 500 characters")
    private String remarks;
}
