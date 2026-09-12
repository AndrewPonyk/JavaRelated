package com.example.a4_fitness_tracker_app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.a4_fitness_tracker_app.domain.model.IntensityLevel
import com.example.a4_fitness_tracker_app.domain.model.Workout
import com.example.a4_fitness_tracker_app.domain.model.WorkoutType

@Composable
fun WorkoutCard(
    workout: Workout,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Workout Type Icon badge
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getWorkoutIcon(workout.type),
                    contentDescription = workout.type.name,
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Workout Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = workout.type.name.replace("_", " "),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${workout.durationMinutes} mins",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = " • ",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${workout.caloriesBurned} kcal",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Intensity Badge
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = when (workout.intensity) {
                    IntensityLevel.HIGH, IntensityLevel.EXTREME -> MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                    IntensityLevel.MODERATE -> MaterialTheme.colorScheme.secondaryContainer
                    IntensityLevel.LOW -> MaterialTheme.colorScheme.primaryContainer
                }
            ) {
                Text(
                    text = workout.intensity.name,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = when (workout.intensity) {
                        IntensityLevel.HIGH, IntensityLevel.EXTREME -> MaterialTheme.colorScheme.error
                        IntensityLevel.MODERATE -> MaterialTheme.colorScheme.secondary
                        IntensityLevel.LOW -> MaterialTheme.colorScheme.primary
                    }
                )
            }
        }
    }
}

fun getWorkoutIcon(type: WorkoutType): ImageVector {
    return when (type) {
        WorkoutType.RUNNING -> Icons.Default.DirectionsRun
        WorkoutType.CYCLING -> Icons.Default.DirectionsBike
        WorkoutType.SWIMMING -> Icons.Default.Pool
        WorkoutType.STRENGTH_TRAINING -> Icons.Default.FitnessCenter
        WorkoutType.HIIT -> Icons.Default.Bolt
        WorkoutType.YOGA -> Icons.Default.SelfImprovement
        WorkoutType.WALKING -> Icons.Default.DirectionsWalk
    }
}
