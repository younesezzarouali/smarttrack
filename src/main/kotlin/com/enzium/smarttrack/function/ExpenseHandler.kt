package com.enzium.smarttrack.function

import com.enzium.smarttrack.domain.model.Expense
import com.enzium.smarttrack.domain.service.ExpenseService
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Component
import java.util.function.Function

@Component
class ExpenseHandler(private val service: ExpenseService) {
    @Bean
    fun addExpense(): Function<Expense, Expense> = Function { expense ->
        service.addExpense(expense)
    }
}