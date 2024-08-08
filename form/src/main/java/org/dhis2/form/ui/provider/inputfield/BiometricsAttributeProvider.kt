package org.dhis2.form.ui.provider.inputfield

import androidx.compose.runtime.Composable
import org.dhis2.commons.biometrics.getBioIconSuccess
import org.dhis2.commons.biometrics.getBioIconWarning
import org.dhis2.commons.resources.ResourceManager
import org.dhis2.form.model.biometrics.BiometricsAttributeUiModelImpl
import org.dhis2.form.ui.biometrics.components.biometricsTEIRegistration.BiometricsTEIRegistration
import org.dhis2.form.ui.biometrics.components.biometricsTEIRegistration.BiometricsTEIState

@Composable
internal fun ProvideBiometricsAttribute(
    fieldUiModel: BiometricsAttributeUiModelImpl,
    resources: ResourceManager,
) {
    BiometricsTEIRegistration(
        value = fieldUiModel.value,
        ageUnderThreshold = fieldUiModel.ageUnderThreshold,
        enabled = fieldUiModel.editable,
        onBiometricsClick = fieldUiModel::onBiometricsClick,
        onSaveWithoutBiometrics = fieldUiModel::onSaveWithoutBiometrics,
        registerLastAndSave = fieldUiModel::registerLastAndSave
    ) { state ->
        when (state) {
            BiometricsTEIState.INITIAL -> null
            BiometricsTEIState.SUCCESS -> resources.context.getBioIconSuccess()
            BiometricsTEIState.FAILURE -> resources.context.getBioIconWarning()
        }
    }
}