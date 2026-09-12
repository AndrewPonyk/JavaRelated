package com.example.a4_fitness_tracker_app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.a4_fitness_tracker_app.domain.model.Goal
import com.example.a4_fitness_tracker_app.domain.model.GoalType

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey
    val id: String,
    val userId: String,
    val type: String,
    val targetValue: Double,
    val currentValue: Double,
    val isCompleted: Boolean
) {
    fun toDomain(): Goal = Goal(
        id = id,
        userId = userId,
        type = GoalType.valueOf(type),
        targetValue = targetValue,
        currentValue = currentValue,
        isCompleted = isCompleted
    )

    companion object {
        fun fromDomain(domain: Goal): GoalEntity = GoalEntity(
            id = domain.id,
            userId = domain.userId,
            type = domain.type.name,
            targetValue = domain.targetValue,
            currentValue = domain.currentValue,
            isCompleted = domain.isCompleted
        )
    }
}
