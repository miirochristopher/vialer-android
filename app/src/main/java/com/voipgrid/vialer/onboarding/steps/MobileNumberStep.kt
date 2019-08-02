package com.voipgrid.vialer.onboarding.steps

import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import com.voipgrid.vialer.Preferences
import com.voipgrid.vialer.R
import com.voipgrid.vialer.VialerApplication
import com.voipgrid.vialer.api.models.MobileNumber
import com.voipgrid.vialer.api.models.PhoneAccount
import com.voipgrid.vialer.api.models.SystemUser
import com.voipgrid.vialer.logging.Logger
import com.voipgrid.vialer.middleware.MiddlewareHelper
import com.voipgrid.vialer.onboarding.core.Step
import com.voipgrid.vialer.onboarding.core.onTextChanged
import com.voipgrid.vialer.util.JsonStorage
import com.voipgrid.vialer.util.PhoneNumberUtils
import kotlinx.android.synthetic.main.onboarding_step_mobile_number.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import javax.inject.Inject

class MobileNumberStep : Step(), View.OnClickListener {

    override val layout = R.layout.onboarding_step_mobile_number

    @Inject lateinit var systemUserStorage: JsonStorage<SystemUser>
    @Inject lateinit var phoneAccountStorage: JsonStorage<PhoneAccount>
    @Inject lateinit var preferences: Preferences

    private lateinit var user: SystemUser

    private val outgoingNumber: String?
        get() = if (user.outgoingCli == "suppressed") onboarding?.getString(R.string.supressed_number) else user.outgoingCli

    private val mobileNumber: String
        get() = PhoneNumberUtils.format(mobileNumberTextDialog.text.toString())

    private val hasOutgoingNumber: Boolean
        get() = outgoingNumberTextDialog.text.isNotEmpty()

    private val mobileNumberCallback = MobileNumberCallback()
    private val configureCallback = ConfigureCallback()

    private val logger = Logger(this)

    override fun onResume() {
        super.onResume()
        VialerApplication.get().component().inject(this)

        if (!systemUserStorage.has(SystemUser::class.java)) {
            logger.e("The user has made it to mobile number without a successful login, restart onboarding")
            onboarding?.restart()
            return
        }

        user = systemUserStorage.get(SystemUser::class.java)

        val enableContinueButton: (_: Editable?) -> Unit = {
            button_configure.isEnabled = mobileNumberTextDialog.text.isNotEmpty()
        }

        mobileNumberTextDialog.setText(user.mobileNumber)
        mobileNumberTextDialog.setRightDrawableOnClickListener {
            alert(R.string.phonenumber_info_text_title, R.string.phonenumber_info_text)
        }
        mobileNumberTextDialog.onTextChanged(enableContinueButton)

        outgoingNumberTextDialog.setText(outgoingNumber)
        outgoingNumberTextDialog.onTextChanged(enableContinueButton)

        button_configure.setOnClickListener(this)
        enableContinueButton.invoke(null)
    }

    override fun onClick(view: View?) {
        if (!PhoneNumberUtils.isValidMobileNumber(mobileNumber)) {
            error(R.string.invalid_mobile_number_message, R.string.invalid_mobile_number_message)
            return
        }

        button_configure.isEnabled = false

        if (hasOutgoingNumber) {
            processMobileAndOutgoingNumber(mobileNumber)
            return
        }

        AlertDialog.Builder(onboarding)
                .setTitle(R.string.onboarding_account_configure_no_outgoing_number_title)
                .setMessage(R.string.onboarding_account_configure_no_outgoing_number)
                .setPositiveButton(android.R.string.yes) { _, _ -> processMobileAndOutgoingNumber(mobileNumber) }
                .setNegativeButton(android.R.string.no, null)
                .show()
    }

    private fun processMobileAndOutgoingNumber(mobileNumber: String) {
        if (!PhoneNumberUtils.isValidMobileNumber(mobileNumber)) return

        voipgridApi.mobileNumber(MobileNumber(mobileNumber)).enqueue(mobileNumberCallback)
    }

    private fun error() {
        error(R.string.onboarding_account_configure_failed_title, R.string.onboarding_account_configure_invalid_phone_number)
        button_configure.isEnabled = true
    }

    private inner class MobileNumberCallback: Callback<MobileNumber> {

        override fun onResponse(call: Call<MobileNumber>, response: Response<MobileNumber>) {
            if (!response.isSuccessful) {
                return
            }

            logger.i("Received a successful mobile number response, looking for a linked phone account")

            val systemUser = user
            systemUser.mobileNumber = mobileNumber
            systemUser.outgoingCli = outgoingNumber
            systemUserStorage.save(user)

            if (systemUser.phoneAccountId != null) {
                onboarding?.isLoading = true
                voipgridApi.phoneAccount(systemUser.phoneAccountId).enqueue(configureCallback)
            } else {
                logger.w("There is no linked voip account, prompt user to configure one")
                preferences.setSipEnabled(false)
                state.hasVoipAccount = false
                onboarding?.progress()
            }
        }

        override fun onFailure(call: Call<MobileNumber>, t: Throwable) {
            error(R.string.onboarding_account_configure_failed_title, R.string.onboarding_account_configure_failed_title)
        }
    }

    private inner class ConfigureCallback: Callback<PhoneAccount> {
        override fun onResponse(call: Call<PhoneAccount>, response: Response<PhoneAccount>) {
            if (!response.isSuccessful) {
                error()
                return
            }

            logger.i("Successfully found a linked phone account")

            phoneAccountStorage.save(response.body() as PhoneAccount)

            if (preferences.hasSipPermission()) {
                MiddlewareHelper.registerAtMiddleware(onboarding)
            }

            onboarding?.progress()
        }

        override fun onFailure(call: Call<PhoneAccount>, t: Throwable) {
            error()
        }
    }
}

/**
 * Listens for a click on the drawable on the right hand side of the edit text
 * and executes the callback.
 *
 */
private fun EditText.setRightDrawableOnClickListener(callback: () -> Unit) {
    setOnTouchListener { _: View, event: MotionEvent -> Boolean
        val right = 2

        if (event.action == MotionEvent.ACTION_UP) {
            if (event.rawX >= (this.right - compoundDrawables[right].bounds.width())) {
                callback()
                return@setOnTouchListener true
            }
        }

        return@setOnTouchListener false
    }
}