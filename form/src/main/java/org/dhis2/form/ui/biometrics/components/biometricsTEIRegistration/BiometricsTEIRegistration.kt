package org.dhis2.form.ui.biometrics.components.biometricsTEIRegistration

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.dhis2.commons.biometrics.BIOMETRICS_FAILURE_PATTERN
import org.dhis2.commons.biometrics.BIOMETRICS_SEARCH_PATTERN
import org.dhis2.commons.biometrics.declinedButtonColor
import org.dhis2.commons.biometrics.successButtonColor
import org.dhis2.form.R

enum class BiometricsTEIState {
    INITIAL,
    SUCCESS,
    FAILURE
}

@Composable
fun BiometricsTEIRegistration(
    value: String?,
    ageUnderThreshold: Boolean,
    enabled: Boolean,
    onBiometricsClick: () -> Unit,
    onSaveWithoutBiometrics: () -> Unit,
    registerLastAndSave: (sessionId: String) -> Unit,
    getIconByState: (BiometricsTEIState) -> Int?
) {
    var linkLastBiometrics by remember { mutableStateOf(true) }

    val biometricsSearchSessionId = remember(value) {
        if (!value.isNullOrEmpty()) {
            if (value.startsWith(BIOMETRICS_SEARCH_PATTERN)) {
                value.split("_")[2]
            } else {
                null
            }
        } else {
            null
        }

    }

    val biometricsState = remember(value) {
        if (!value.isNullOrEmpty()) {
            if (value.startsWith(BIOMETRICS_FAILURE_PATTERN)) {
                BiometricsTEIState.FAILURE
            } else if (value.startsWith(BIOMETRICS_SEARCH_PATTERN)) {
                BiometricsTEIState.INITIAL
            } else {
                BiometricsTEIState.SUCCESS
            }
        } else {
            BiometricsTEIState.INITIAL
        }
    }

    Column {
        if (biometricsSearchSessionId != null && !ageUnderThreshold) {
            LinkLastBiometricsCheckBox(
                value = linkLastBiometrics,
                enabled = enabled,
                onCheckedChange = { linkLastBiometrics = it }
            )
        }

        Spacer(modifier = Modifier.height(96.dp))

        if (linkLastBiometrics && biometricsSearchSessionId != null && !ageUnderThreshold) {
            LinkLastBiometricsNextButton(enabled = enabled) {
                registerLastAndSave(
                    biometricsSearchSessionId
                )
            }
        } else {
            if (!ageUnderThreshold) {
                RegistrationButton(
                    biometricsState = biometricsState,
                    enabled = enabled,
                    onBiometricsClick = onBiometricsClick,
                    getIconByState = getIconByState
                )
            }

            SaveWithoutBiometricsButton(
                enabled = enabled,
                onClick = onSaveWithoutBiometrics
            )
        }
    }
}

@Composable
fun RegistrationButton(
    biometricsState: BiometricsTEIState,
    enabled: Boolean,
    onBiometricsClick: () -> Unit,
    getIconByState: (BiometricsTEIState) -> Int?
) {

    when (biometricsState) {
        BiometricsTEIState.INITIAL -> {
            RegistrationResult(
                onBiometricsClick = onBiometricsClick,
                enabled = enabled,
                resultText = R.string.biometrics_register_and_save,
                resultIcon = getIconByState(BiometricsTEIState.INITIAL),
                resultColor = null,
                showRetake = false
            )
        }

        BiometricsTEIState.SUCCESS -> {
            RegistrationResult(
                onBiometricsClick = onBiometricsClick,
                enabled = enabled,
                resultText = R.string.biometrics_completed,
                resultIcon = getIconByState(BiometricsTEIState.SUCCESS),
                resultColor = successButtonColor,
                showRetake = false
            )
        }
        BiometricsTEIState.FAILURE -> {
            BiometricsStatusFlag(
                text = stringResource(id = R.string.biometrics_declined),
                backgroundColor = declinedButtonColor,
                icon = getIconByState(BiometricsTEIState.FAILURE) ?: R.drawable.ic_bio_face_failed
            )
        }
    }
}

@Preview(showBackground = true, name = "Initial After biometrics search")
@Composable
fun PreviewBiometricsTEIRegistrationInitialAfterSearch() {

    BiometricsTEIRegistration(
        value = BIOMETRICS_SEARCH_PATTERN,
        enabled = true,
        ageUnderThreshold = false,
        onBiometricsClick = { },
        onSaveWithoutBiometrics = {},
        registerLastAndSave = { },
        getIconByState = { null }
    )
}

@Preview(showBackground = true, name = "Initial State disabled")
@Composable
fun PreviewBiometricsTEIRegistrationInitialDisabled() {

    BiometricsTEIRegistration(
        value = null,
        enabled = false,
        ageUnderThreshold = false,
        onBiometricsClick = { },
        onSaveWithoutBiometrics = {},
        registerLastAndSave = { },
        getIconByState = { null }
    )
}

@Preview(showBackground = true, name = "Initial After biometrics search disabled")
@Composable
fun PreviewBiometricsTEIRegistrationInitialAfterSearchDisabled() {

    BiometricsTEIRegistration(
        value = BIOMETRICS_SEARCH_PATTERN,
        enabled = false,
        ageUnderThreshold = false,
        onBiometricsClick = { },
        onSaveWithoutBiometrics = {},
        registerLastAndSave = { },
        getIconByState = { null }
    )
}

@Preview(showBackground = true, name = "Initial State")
@Composable
fun PreviewBiometricsTEIRegistrationInitial() {

    BiometricsTEIRegistration(
        value = null,
        enabled = true,
        ageUnderThreshold = false,
        onBiometricsClick = { },
        onSaveWithoutBiometrics = {},
        registerLastAndSave = { },
        getIconByState = { null }
    )
}

@Preview(showBackground = true, name = "Success State")
@Composable
fun PreviewBiometricsTEIRegistrationSuccess() {
    BiometricsTEIRegistration(
        value = "927232-2-323-2-32-32-32",
        enabled = true,
        ageUnderThreshold = false,
        onBiometricsClick = { },
        onSaveWithoutBiometrics = {},
        registerLastAndSave = { },
        getIconByState = { R.drawable.ic_bio_face_success }
    )
}

@Preview(showBackground = true, name = "Failure State")
@Composable
fun PreviewBiometricsTEIRegistrationFailure() {
    BiometricsTEIRegistration(
        value = BIOMETRICS_FAILURE_PATTERN,
        enabled = true,
        ageUnderThreshold = false,
        onBiometricsClick = { },
        onSaveWithoutBiometrics = {},
        registerLastAndSave = { },
        getIconByState = { R.drawable.ic_bio_face_failed }
    )
}




