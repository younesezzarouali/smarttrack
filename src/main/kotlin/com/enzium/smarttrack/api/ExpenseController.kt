package com.enzium.smarttrack.api

import com.enzium.smarttrack.domain.model.Expense
import com.enzium.smarttrack.domain.service.ExpenseService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

// API
@RestController
@RequestMapping("/expenses")
class ExpenseController(private val service: ExpenseService) {

    @PostMapping
    fun add(@RequestBody expense: Expense): Expense = service.addExpense(expense)

    @GetMapping
    fun all(): List<Expense> = service.getAll()

    @GetMapping("/summary")
    fun summary(): ExpenseSummaryDto = service.getExpenseSummary()
}