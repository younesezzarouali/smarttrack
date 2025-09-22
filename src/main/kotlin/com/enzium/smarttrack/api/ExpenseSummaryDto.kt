package com.enzium.smarttrack.api

import com.enzium.smarttrack.domain.model.ExpenseCategory

data class ExpenseSummaryDto(
    val totalByCategory: Map<ExpenseCategory, Double>,
    val total: Double
)

