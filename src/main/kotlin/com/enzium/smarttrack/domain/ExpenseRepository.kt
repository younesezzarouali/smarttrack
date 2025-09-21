package com.enzium.smarttrack.domain

import com.enzium.smarttrack.domain.model.Expense
import org.springframework.data.jpa.repository.JpaRepository

interface ExpenseRepository : JpaRepository<Expense, Long>