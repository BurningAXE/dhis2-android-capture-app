package org.dhis2.usescases.biometrics

import androidx.compose.material.Icon
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import org.dhis2.R
import org.dhis2.form.extensions.isBiometricText
import org.hisp.dhis.mobile.ui.designsystem.component.AdditionalInfoItem

fun addAttrBiometricsIconIfRequired(additionalInfo: List<AdditionalInfoItem>): List<AdditionalInfoItem> {
    return if (BIOMETRICS_ENABLED) additionalInfo.map {
        val key = it.key ?: ""

        if (key.isBiometricText()) {
            it.copy(value = "", icon = {
                Icon(
                    painter = if (it.value.isNotBlank() && it.value != "-") painterResource(R.drawable.ic_bio_available_yes)
                    else painterResource(R.drawable.ic_bio_available_no),
                    tint = Color.Unspecified,
                    contentDescription = null,
                )
            })
        } else {
            it
        }

    } else additionalInfo
}