package org.dhis2.form.model.biometrics

interface BiometricsRegistrationUIModel {
    fun onBiometricsClick()
}

interface BiometricsTEIRegistrationUIModel: BiometricsRegistrationUIModel {
    fun onSaveWithoutBiometrics()
}