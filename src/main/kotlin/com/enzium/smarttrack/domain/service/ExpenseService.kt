package com.enzium.smarttrack.domain.service

import com.enzium.smarttrack.api.ExpenseSummaryDto
import com.enzium.smarttrack.domain.model.Expense
import com.enzium.smarttrack.domain.model.ExpenseCategory
import com.enzium.smarttrack.domain.ExpenseRepository
import org.springframework.stereotype.Service


@Service
class ExpenseService(private val repo: ExpenseRepository) {
    fun addExpense(expense: Expense): Expense = repo.save(expense)
    fun getAll(): List<Expense> = repo.findAll()

    fun getExpenseSummary(): ExpenseSummaryDto {
        val expenses = repo.findAll()
        val totalByCategory = ExpenseCategory.values().associateWith { category ->
            expenses.filter { it.category == category }.sumOf { it.amount }
        }
        val total = expenses.sumOf { it.amount }
        return ExpenseSummaryDto(totalByCategory, total)
    }
}
