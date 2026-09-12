package com.example.inventory.stock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.inventory.common.error.BusinessRuleException;
import com.example.inventory.stock.domain.BarcodeSymbology;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BarcodeValidatorTest {
    private final BarcodeValidator validator = new BarcodeValidator();

    @ParameterizedTest
    @CsvSource({
        "4006381333931,EAN_13", "96385074,EAN_8", "036000291452,UPC_A",
        "ABC-123,CODE_39", "mixed value,CODE_128", "https://item/1,QR_CODE", "alias_1,UNKNOWN"
    })
    void acceptsSupportedBarcodes(String value, BarcodeSymbology symbology) {
        assertThat(validator.validateAndNormalize(value, symbology)).isEqualTo(value.toUpperCase());
    }

    @ParameterizedTest
    @CsvSource({"4006381333932,EAN_13", "123,EAN_8", "bad!,UNKNOWN", "abc@,CODE_39"})
    void rejectsInvalidBarcodes(String value, BarcodeSymbology symbology) {
        assertThatThrownBy(() -> validator.validateAndNormalize(value, symbology))
                .isInstanceOf(BusinessRuleException.class);
    }
}
