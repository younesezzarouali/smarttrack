package com.enzium.smarttrack.function

import com.enzium.smarttrack.domain.model.ExpenseCategory

data class ExpenseSummaryDto(
    val totalByCategory: Map<ExpenseCategory, Double>,
    val total: Double
)