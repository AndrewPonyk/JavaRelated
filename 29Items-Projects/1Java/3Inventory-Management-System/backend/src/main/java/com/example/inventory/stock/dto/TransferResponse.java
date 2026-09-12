package com.example.inventory.stock.dto;

import java.util.UUID;

public record TransferResponse(
        UUID transferId,
        UUID sourceItemId,
        UUID destinationItemId,
        long quantity,
        long sourceOnHand,
        long destinationOnHand) {
}

