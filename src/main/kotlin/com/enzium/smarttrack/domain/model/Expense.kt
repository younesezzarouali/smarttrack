package com.enzium.smarttrack.domain.model

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import java.util.Date

@Entity
data class Expense(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: String? = null,
    val label: String,
    val amount: Double,

    @Enumerated(EnumType.STRING)
    val category: ExpenseCategory = ExpenseCategory.OTHER,

    val createdAt: Date = Date()
)