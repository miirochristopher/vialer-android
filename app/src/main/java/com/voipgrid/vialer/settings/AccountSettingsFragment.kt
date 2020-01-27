package com.voipgrid.vialer.settings

import android.os.Bundle
import android.text.InputType
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import com.voipgrid.vialer.R
import com.voipgrid.vialer.User
import com.voipgrid.vialer.api.models.MobileNumber
import com.voipgrid.vialer.util.PhoneNumberUtils
import com.voipgrid.vialer.util.Sim
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject


class AccountSettingsFragment : AbstractSettingsFragment() {

    private val sim: Sim by inject()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_account, rootKey)

        findPreference<EditTextPreference>("name")?.summaryProvider = Preference.SummaryProvider<EditTextPreference> { User.voipgridUser?.fullName }
        findPreference<EditTextPreference>("username")?.summaryProvider = Preference.SummaryProvider<EditTextPreference> { User.voipgridUser?.email }
        findPreference<EditTextPreference>("description")?.summaryProvider = Preference.SummaryProvider<EditTextPreference> { User.voipAccount?.description }
        findPreference<EditTextPreference>("account_id")?.summaryProvider = Preference.SummaryProvider<EditTextPreference> { User.voipAccount?.accountId }
        findPreference<EditTextPreference>("outgoing_number")?.summaryProvider = Preference.SummaryProvider<EditTextPreference> { User.voipgridUser?.outgoingCli }
        findPreference<Preference>("mobile_number_not_matching")?.apply {
            isVisible = sim.mobileNumber != null && !configuredMobileNumberMatchesSimPhoneNumber()
            summaryProvider = Preference.SummaryProvider<Preference> { if (sim.mobileNumber != null) sim.mobileNumber else "" }
            setOnPreferenceClickListener {
                sim.mobileNumber?.let { mobileNumberChanged(it) }
                true
            }
        }

        findPreference<EditTextPreference>("mobile_number")?.apply {
            setOnBindEditTextListener { editText ->
                editText.inputType = InputType.TYPE_CLASS_PHONE
                editText.text.clear()
                editText.text.insert(0, User.voipgridUser?.mobileNumber)
            }
            summaryProvider = Preference.SummaryProvider<EditTextPreference> { User.voipgridUser?.mobileNumber }
            setOnChangeListener(networkConnectivityRequired = true) { newValue: String -> mobileNumberChanged(newValue) }
        }
    }

    /**
     * Validate the mobile number, send the change request to the server and then refresh our user to
     * confirm that the change has been applied.
     *
     */
    private fun mobileNumberChanged(newNumber: String) : Boolean {
        if (!PhoneNumberUtils.isValidMobileNumber(newNumber)) {
            alert(R.string.phonenumber_info_text_title, R.string.onboarding_account_configure_invalid_phone_number)
            return false
        }

        val number: String = PhoneNumberUtils.formatMobileNumber(newNumber)

        isLoading = true

        GlobalScope.launch(Dispatchers.IO) {
            val response = voipgridApi.mobileNumber(MobileNumber(number)).execute()

            if (!response.isSuccessful) {
                alert(R.string.phonenumber_info_text_title, R.string.onboarding_account_configure_invalid_phone_number)
                return@launch
            }

            userSynchronizer.sync()

            activity?.runOnUiThread {
                findPreference<Preference>("mobile_number")?.refreshSummary()
                findPreference<Preference>("mobile_number_not_matching")?.isVisible = sim.mobileNumber != null && !configuredMobileNumberMatchesSimPhoneNumber()
                isLoading = false
            }
        }

        return true
    }

    private fun configuredMobileNumberMatchesSimPhoneNumber() : Boolean =
            sim.mobileNumber == User.voipgridUser?.mobileNumber
}