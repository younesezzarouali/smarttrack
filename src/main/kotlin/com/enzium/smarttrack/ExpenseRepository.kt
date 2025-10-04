package com.enzium.smarttrack

import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepository
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class ExpenseRepository: PanacheRepository<Expense> {
}