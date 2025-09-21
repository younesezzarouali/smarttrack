package com.enzium.smarttrack.domain.service

import com.enzium.smarttrack.domain.model.Expense
import com.enzium.smarttrack.domain.ExpenseRepository
import org.springframework.stereotype.Service


@Service
class ExpenseService(private val repo: ExpenseRepository) {
    fun addExpense(expense: Expense): Expense = repo.save(expense)
    fun getAll(): List<Expense> = repo.findAll()
}
