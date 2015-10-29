package com.voipgrid.vialer;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.google.android.gms.gcm.GcmListenerService;
import com.squareup.okhttp.OkHttpClient;
import com.voipgrid.vialer.api.Registration;
import com.voipgrid.vialer.api.ServiceGenerator;
import com.voipgrid.vialer.sip.SipConstants;
import com.voipgrid.vialer.sip.SipService;
import com.voipgrid.vialer.sip.SipUri;
import com.voipgrid.vialer.util.ConnectivityHelper;
import com.voipgrid.vialer.util.Middleware;

import retrofit.Callback;
import retrofit.RetrofitError;
import retrofit.client.OkClient;


/**
 * Listen to messages from GCM. The backend server sends us GCM notifications when we have
 * incoming calls.
 */
public class VialerGcmListenerService extends GcmListenerService implements Middleware.Constants {
    private final static String TAG = VialerGcmListenerService.class.getSimpleName();
    /* Message format constants. */
    private final static String MESSAGE_TYPE = "type";

    private final static String CHECKIN_REQUEST_TYPE = "checkin";
    private final static String CALL_REQUEST_TYPE = "call";
    private final static String MESSAGE_REQUEST_TYPE = "message";

    private final static String RESPONSE_URL = "response_api";
    private final static String REQUEST_TOKEN = "unique_key";
    private final static String PHONE_NUMBER = "phonenumber";
    private final static String CALLER_ID = "caller_id";
    private static final String SUPRESSED = "supressed";
    private ConnectivityHelper mConnectivityHelper;


    @Override
    public void onMessageReceived(String from, Bundle data) {
        Log.d(TAG, String.format("Received GCM message from %s", from));
        Log.d(TAG, String.format("Data %s", data));

        String request = data.getString(MESSAGE_TYPE, "");
        if (request.equals(CHECKIN_REQUEST_TYPE)) {

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            String token = prefs.getString(CURRENT_TOKEN, "");
            if (!token.isEmpty()) {
                /* Use passed URL and token to identify ourselves */
                replyServer(data.getString(RESPONSE_URL), token, true);
            }
        } else if (request.equals(CALL_REQUEST_TYPE)) {
            ConnectivityHelper connectivityHelper = new ConnectivityHelper(
                    (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE),
                    (TelephonyManager) getSystemService(TELEPHONY_SERVICE));
            if(connectivityHelper.hasNetworkConnection() && connectivityHelper.hasFastData()) {

                String number = data.getString(PHONE_NUMBER);
                if(number.equalsIgnoreCase(SUPRESSED)) {
                    number = getString(R.string.supressed_number);
                }

                /* First start the SIP service with an incoming call */
                startSipService(
                        number,
                        data.getString(CALLER_ID),
                        data.getString(RESPONSE_URL),
                        data.getString(REQUEST_TOKEN)
                );
            } else {
                /* Inform the middleware the incoming call is received but the app can not handle
                   the sip call because there is no LTE or Wifi connection available at this point */
                replyServer(data.getString(RESPONSE_URL), data.getString(REQUEST_TOKEN), false);
            }

        } else if (request.equals(MESSAGE_REQUEST_TYPE)) {
            // TODO: notify a user of message in payload.
        }
    }

    /**
     * Notify the middleware server that we are, in fact, alive.
     * @param responseUrl the URL of the server
     * @param requestToken unique_key for middleware for recognising SIP connection status updates.
     */
    private void replyServer(String responseUrl, String requestToken, boolean isAvailable) {
        mConnectivityHelper = new ConnectivityHelper(
                (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE),
                (TelephonyManager) getSystemService(TELEPHONY_SERVICE)
        );

        Registration registrationApi = ServiceGenerator.createService(
                mConnectivityHelper,
                Registration.class,
                responseUrl,
                new OkClient(new OkHttpClient())
        );

        registrationApi.reply(requestToken, isAvailable, new Callback<Object>() {
            @Override
            public void success(Object object, retrofit.client.Response response) {
                Log.d(TAG, "response: " + response.getStatus());
            }

            @Override
            public void failure(RetrofitError error) {
                Log.d(TAG, "error: " + error.getMessage());
            }
        });
    }

    /**
     *
     * @param phoneNumber the number that tried call in.
     * @param callerId pretty name of the phonenumber that tied to call in.
     */
    private void startSipService(String phoneNumber, String callerId, String url, String token) {
        Intent intent = new Intent(this, SipService.class);
        intent.setAction(SipConstants.ACTION_VIALER_INCOMING);

        // set a phoneNumberUri as DATA for the intent to SipServiceOld.
        intent.setData(SipUri.build(this, phoneNumber));

        intent.putExtra(SipConstants.EXTRA_RESPONSE_URL, url);
        intent.putExtra(SipConstants.EXTRA_REQUEST_TOKEN, token);
        intent.putExtra(SipConstants.EXTRA_PHONE_NUMBER, phoneNumber);
        intent.putExtra(SipConstants.EXTRA_CONTACT_NAME, callerId);

        startService(intent);
    }
}
