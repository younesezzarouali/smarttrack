package com.enzium.smarttrack.function

import com.enzium.smarttrack.domain.service.ExpenseService
import org.springframework.stereotype.Component
import java.util.function.Supplier

@Component
class ExpenseSummaryHandler (private val service: ExpenseService): Supplier<ExpenseSummaryDto> {
    override fun get(): ExpenseSummaryDto {
        return service.getExpenseSummary()
    }
}