package org.dhis2.form.ui.biometrics.components.biometricsTEIRegistration

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.dhis2.commons.biometrics.defaultButtonColor

@Composable
internal fun SaveWithoutBiometricsButton(
    enabled: Boolean,
    onClick: (() -> Unit),
) {
    val color = if (enabled) Color(
        android.graphics.Color.parseColor(
            defaultButtonColor
        )
    ) else Color.LightGray

    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .padding(1.dp)
            .border(
                1.dp, color, shape = MaterialTheme.shapes.small,
            ),

        ) {
        Text("Save without biometrics".uppercase(), color = color)
    }
}
