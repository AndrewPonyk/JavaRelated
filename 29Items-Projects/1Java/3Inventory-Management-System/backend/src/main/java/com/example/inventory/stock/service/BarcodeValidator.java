package com.example.inventory.stock.service;

import com.example.inventory.common.error.BusinessRuleException;
import com.example.inventory.stock.domain.BarcodeSymbology;
import org.springframework.stereotype.Component;

@Component
public class BarcodeValidator {

    public String validateAndNormalize(String raw, BarcodeSymbology symbology) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessRuleException("Barcode is required.");
        }
        String barcode = raw.trim().toUpperCase();
        if (barcode.length() > 128 || !barcode.matches("[\\x20-\\x7E]+")) {
            throw new BusinessRuleException("Barcode contains unsupported characters or is too long.");
        }
        boolean valid = switch (symbology) {
            case EAN_8 -> barcode.matches("\\d{8}") && hasValidGs1CheckDigit(barcode);
            case EAN_13 -> barcode.matches("\\d{13}") && hasValidGs1CheckDigit(barcode);
            case UPC_A -> barcode.matches("\\d{12}") && hasValidGs1CheckDigit(barcode);
            case CODE_39 -> barcode.matches("[0-9A-Z .$/+%*-]{1,43}");
            case CODE_128 -> barcode.length() <= 80;
            case QR_CODE -> barcode.length() <= 128;
            case UNKNOWN -> barcode.matches("[A-Z0-9._/+:=-]{1,128}");
        };
        if (!valid) {
            throw new BusinessRuleException("Barcode does not match " + symbology + " rules.");
        }
        return barcode;
    }

    private boolean hasValidGs1CheckDigit(String barcode) {
        int sum = 0;
        boolean multiplyByThree = true;
        for (int index = barcode.length() - 2; index >= 0; index--) {
            int digit = barcode.charAt(index) - '0';
            sum += digit * (multiplyByThree ? 3 : 1);
            multiplyByThree = !multiplyByThree;
        }
        int expected = (10 - sum % 10) % 10;
        return expected == barcode.charAt(barcode.length() - 1) - '0';
    }
}

