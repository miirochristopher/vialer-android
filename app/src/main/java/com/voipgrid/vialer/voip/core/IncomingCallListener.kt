package com.voipgrid.vialer.voip.core

import com.voipgrid.vialer.voip.core.call.Call
import com.voipgrid.vialer.voip.core.call.State

internal interface VoipListener {

    fun onIncomingCallFromVoipProvider(call: Call)

    fun onCallStateUpdate(call: Call, state: State)

    fun onRegister()
}