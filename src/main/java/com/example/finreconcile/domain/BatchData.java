package com.example.finreconcile.domain;

import java.util.List;

public record BatchData(List<OrderRecord> orders, List<PaymentRecord> payments) {
}
