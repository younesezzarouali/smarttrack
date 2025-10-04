package com.enzium.smarttrack

import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntity
import jakarta.persistence.Entity
import java.time.Instant

@Entity
class Expense : PanacheEntity() {
    var label: String = ""
    var amount: Double = 0.0
    var category: String = "OTHER"
    var createdAt: Instant = Instant.now()
}