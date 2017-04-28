package com.example.android.sip;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.*;
import android.view.View.OnClickListener;
import android.net.sip.*;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.ToggleButton;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.StringTokenizer;

import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.content.BroadcastReceiver;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import java.util.List;

/**
 * Handles all calling, receiving calls, and UI interaction in the MainActivity
 * app.
 */
public class MainActivity extends Activity  {

    public String sipAddress = null;
    public String old_sipAddress = null;
    private ArrayList<String> addList = new ArrayList<String>();
    public SipManager manager = null;
    public SipProfile me = null;
    public SipAudioCall call = null;

    public IncomingCallReceiver callReceiver;

    private static final int CALL_ADDRESS = 5;
    private static final int ADDRESS_LIST = 1;
    private static final int SET_AUTH_INFO = 2;
    private static final int UPDATE_SETTINGS_DIALOG = 3;
    private static final int HANG_UP = 4;

    WifiManager wifiManager;
    WifiInfo wifiInfo;
    WifiConfiguration wfc;
    NetworkInfo networkInfo;
    int networkID;
    String ssid = "AndroidAP1";
    String password = "connectMe";
    List<ScanResult> wifiList;
    TelephonyManager tel;
    MyPhoneStateListener myPhoneStateListener;
    public static final int UNKNOW_CODE = 99;
    int MAX_SIGNAL_DBM_VALUE = 31;
    public int sStrength;
    int rssi;
    int expTime = 20;
    TextView textCellular;
    TextView textWifi;
    TextView textType;
    sipRegistration sipListener = new sipRegistration();
    SipProfile.Builder builder;

    SipAudioCall.Listener listener;

    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.walkietalkie);

        Button pushToEnd = (Button) findViewById(R.id.pushToEnd);
        pushToEnd.setOnClickListener(new handles());

        myPhoneStateListener = new MyPhoneStateListener();
        tel = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        tel.listen(myPhoneStateListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
        textWifi = (TextView) findViewById(R.id.wifi_rssi);
        textCellular = (TextView) findViewById(R.id.gsm_rssi);
        textType = (TextView) findViewById(R.id.network_type);
        setWifiStrength();
        // Set up the intent filter. This will be used to fire an
        // IncomingCallReceiver when someone calls the SIP address used by this
        // application.
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.SipDemo.INCOMING_CALL");
        callReceiver = new IncomingCallReceiver();
        this.registerReceiver(callReceiver, filter);
        this.registerReceiver(this.myWifiReceiver,
                new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        Log.v("call Initializemanager ", "call Initializemanager ");
        initializeManager();
    }

    @Override
    protected void onResume() {
        this.registerReceiver(this.myRssiChangeReceiver,
                new IntentFilter(WifiManager.RSSI_CHANGED_ACTION));
        registerReceiver(receiverWifi, new IntentFilter(
                WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        if(sipAddress != null )
        {
            old_sipAddress = sipAddress;
        }
        super.onResume();
        initializeManager();

    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(myRssiChangeReceiver);
        unregisterReceiver(myWifiReceiver);
        unregisterReceiver(receiverWifi);
        if (call != null) {
            call.close();
        }

        closeLocalProfile();

        if (callReceiver != null) {
            this.unregisterReceiver(callReceiver);
        }
    }

    public void initializeManager() {
        if (manager == null) {
            manager = SipManager.newInstance(this);
        }
        initializeLocalProfile();
    }

    /**
     * Logs you into your SIP provider, registering this device as the location
     * to send SIP calls to for your SIP address.
     */
    public void initializeLocalProfile() {
        Log.d("Manager", String.valueOf(manager));
        if (manager == null) {
            return;
        }
        Log.d("me", String.valueOf(me));
        if (me != null) {
            closeLocalProfile();
        }

        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(getBaseContext());
        String username = prefs.getString("namePref", "");
        String domain = prefs.getString("domainPref", "");
        String password = prefs.getString("passPref", "");

        Log.d("username", username);
        Log.d("domain", domain);
        Log.d("password", password);

        if (username.length() == 0 || domain.length() == 0
                || password.length() == 0) {
            showDialog(UPDATE_SETTINGS_DIALOG);
            return;
        }

        try {
            builder = new SipProfile.Builder(username,
                    domain);
            builder.setPassword(password);
            me = builder.build();

            Log.d("me", String.valueOf(me));
            Intent i = new Intent();
            i.setAction("android.SipDemo.INCOMING_CALL");
            PendingIntent pi = PendingIntent.getBroadcast(this, 0, i,
                    Intent.FILL_IN_DATA);
            manager.open(me, pi, null);

            // This listener must be added AFTER manager.open is called,
            // Otherwise the methods aren't guaranteed to fire.

            manager.setRegistrationListener(me.getUriString(),
                    new SipRegistrationListener() {
                        public void onRegistering(String localProfileUri) {
                            updateStatus("Registering with SIP Server...");
                        }

                        public void onRegistrationDone(String localProfileUri,
                                                       long expiryTime) {
                            updateStatus("Ready");
                        }

                        public void onRegistrationFailed(
                                String localProfileUri, int errorCode,
                                String errorMessage) {
                            updateStatus("Registration failed.  Please check settings.");
                        }
                    });
        } catch (ParseException pe) {
            updateStatus("Connection Error.");
        } catch (SipException se) {
            updateStatus("Connection error.");
        }
    }


    /**
     * Closes out your local profile, freeing associated objects into memory and
     * unregistering your device from the server.
     */
    public void closeLocalProfile() {
        Log.d("CloseLocalProfile", String.valueOf(manager));
        if (manager == null) {
            return;
        }
        try {
            Log.d("CloseLocalProfile", String.valueOf(me));
            if (me != null) {
                Log.d("close me URI ", me.getUriString());
                manager.close(me.getUriString());
            }
        } catch (Exception ee) {
        }
    }

    /**
     * Make an outgoing call.
     */
    public void initiateCall() {

        updateStatus(sipAddress);

        try {
            listener = new SipAudioCall.Listener() {
                // Much of the client's interaction with the SIP Stack will
                // happen via listeners. Even making an outgoing call, don't
                // forget to set up a listener to set things up once the call is
                // established.
                @Override
                public void onCallEstablished(SipAudioCall call) {
                    Log.d("APP", "inside onCallEstablished");
                    call.startAudio();
                    call.setSpeakerMode(true);
                    updateStatus(call, 2);

                }
                @Override
                public void onRinging(SipAudioCall call, SipProfile caller) {
                    Log.d("APP","inside onRinging");
                    try{
                        Log.d("APP","sipprofile of caller " + caller);
                        call.answerCall(expTime);
                        call.startAudio();
                    } catch (SipException e) {
                        e.printStackTrace();
                    }
                }
                @Override
                public void onCallEnded(SipAudioCall call) {
                    updateStatus("Call End");
                    updateStatus("Ready");
                }

                @Override
                public void onError(SipAudioCall call, int errorCode, String errorMessage) {
                    if (errorCode == -10) {
                        updateStatus("Handover in progress");
                        try {
                            Log.d("APP","about to make handover call");
                            Log.d("APP", old_sipAddress);
                            while(!manager.isRegistered(me.getUriString())){
                                Log.d("APP","inside while loop");
                            }
                            Log.d("APP","outside while loop");
                            sipAddress = old_sipAddress;
                            Log.d("APP",sipAddress);
                            manager.makeAudioCall(me.getUriString(), sipAddress,
                                    listener, 30);
                        } catch (SipException e) {
                            e.printStackTrace();
                        }
                    }
                }
            };
            call = manager.makeAudioCall(me.getUriString(), sipAddress,
                    listener, 30);

        } catch (Exception e) {
            if (me != null) {
                try {
                    manager.close(me.getUriString());
                } catch (Exception ee) {
                    ee.printStackTrace();
                }
            }
            if (call != null) {
                call.close();
            }
        }
    }

    private BroadcastReceiver myRssiChangeReceiver
            = new BroadcastReceiver(){

        @Override
        public void onReceive(Context arg0, Intent arg1) {
            // TODO Auto-generated method stub
            int newRssi = arg1.getIntExtra(WifiManager.EXTRA_NEW_RSSI, 0);
            Log.d("APP",Integer.toString(newRssi));

            if (networkInfo.getType()==ConnectivityManager.TYPE_WIFI){
                if (newRssi <= -70){
                    textWifi.setText("--");
                    wifiDisconnect();
                }
                else {
                    textWifi.setText(String.valueOf(newRssi));
                }
            }
            else{
                textWifi.setText("--");
                textType.setText("Gsm Connected");
            }


        }};

    private BroadcastReceiver myWifiReceiver
            = new BroadcastReceiver(){
        @Override
        public void onReceive(Context arg0, Intent arg1) {
            // TODO Auto-generated method stub
            Log.d("APP","inside connectivity manager");
            networkInfo = (NetworkInfo) arg1.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
            if(networkInfo.getType() == ConnectivityManager.TYPE_WIFI){
                textType.setText("Wifi Connected");
                textWifi.setText(Integer.toString(rssi));
            }
            else{
                textType.setText("Gsm Connected");
                textWifi.setText("--");
            }
        }};

    private BroadcastReceiver receiverWifi = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            wifiList = wifiManager.getScanResults();
            for (int i = 0; i < wifiList.size(); i++){
                Log.d("APP",wifiList.get(i).SSID +":"+wifiList.get(i).level);
                if (wifiList.get(i).SSID.equals(ssid)){
                    Log.d("APP","TRUE");
                }
                if (networkInfo.getType()!=ConnectivityManager.TYPE_WIFI && wifiList.get(i).SSID.equals(ssid) && wifiList.get(i).level >-65){
                    Log.d("APP",wifiList.get(i).SSID);
                    wifiConnect();
                }
                else {
                    continue;
                }
            }
        }
    };

    public class MyPhoneStateListener extends PhoneStateListener {
        /* Get the Signal strength from the provider, each time there is an update */
        @Override
        public void onSignalStrengthsChanged(SignalStrength signalStrength) {
            super.onSignalStrengthsChanged(signalStrength);

            if (null != signalStrength && signalStrength.getGsmSignalStrength() != UNKNOW_CODE) {
                sStrength = 2*(signalStrength.getGsmSignalStrength()) - 113;
                textCellular.setText(Integer.toString(sStrength));
            }
        }
    }

    public void setWifiStrength(){
        wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        wifiInfo = wifiManager.getConnectionInfo();
        setConf();
        wifiManager.startScan();
        this.rssi = wifiInfo.getRssi();
        if ((rssi <= -70)){
            wifiDisconnect();

        }
        else {//assuming the phone is initially connected to wifi and only disconnecting based on signal strength
            textWifi.setText(Integer.toString(rssi));
        }
    }

    public void wifiDisconnect(){

        wifiManager.disconnect();
    }

    public void wifiConnect(){
        wifiManager.enableNetwork(networkID, true);
        wifiManager.reconnect();
        Log.d("APP","reached connect");
    }

    public void setConf(){
        wfc = new WifiConfiguration();

        wfc.SSID = "\"".concat(ssid).concat("\"");
        wfc.status = WifiConfiguration.Status.DISABLED;
        wfc.priority = 40;
        wfc.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
        wfc.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
        wfc.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
        wfc.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
        wfc.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
        wfc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
        wfc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104);
        wfc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
        wfc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
        wfc.preSharedKey = "\"".concat(password).concat("\"");
        networkID = wifiManager.addNetwork(wfc);
    }
    /**
     * Updates the status box at the top of the UI with a messege of your
     * choice.
     *
     * @param status
     *            The String to display in the status box.
     */
    public void updateStatus(final String status) {
        // Be a good citizen. Make sure UI changes fire on the UI thread.
        this.runOnUiThread(new Runnable() {
            public void run() {
                TextView labelView = (TextView) findViewById(R.id.sipLabel);
                labelView.setText(status);
            }
        });
    }

    /**
     * Updates the status box with the SIP address of the current call.
     *
     * @param call
     *            The current, active call.
     */
    public void updateStatus(SipAudioCall call, int val) {
        String useName = call.getPeerProfile().getDisplayName();
        if (useName == null) {
            useName = call.getPeerProfile().getUserName();
        }
        if (val == 1) {
            updateStatus("Incoming Call: " + useName + "@"
                    + call.getPeerProfile().getSipDomain());
        } else {
            updateStatus("Calling: " + useName + "@"
                    + call.getPeerProfile().getSipDomain());
        }

    }


    class handles implements View.OnClickListener{
        @Override
        public void onClick(View v) {
            try {
                call.endCall();
            } catch (SipException e) {
                e.printStackTrace();
            }
        }
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, ADDRESS_LIST, 0, "Phone Call");
        menu.add(0, SET_AUTH_INFO, 0, "SIP Settings");
        menu.add(0, HANG_UP, 0, "End Call");

        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case ADDRESS_LIST:

                showDialog(ADDRESS_LIST);
                break;
            case SET_AUTH_INFO:
                updatePreferences();
                break;
            case HANG_UP:
                Log.v("End call", "true");
                if (call != null) {
                    Log.v("End call not null", "true");
                    try {
                        call.endCall();
                        updateStatus("End Call");
                        updateStatus("Ready");
                    } catch (SipException se) {
                    }
                    call.close();
                }
                break;
        }
        return true;
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case ADDRESS_LIST:

                final Dialog dialog = new Dialog(MainActivity.this);

                dialog.setContentView(R.layout.address_list);
                dialog.setTitle("Address List");

                ListView add_list = (ListView) dialog.findViewById(R.id.add_list);
                Button new_add = (Button) dialog.findViewById(R.id.btn_new_add);

                ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                        android.R.layout.simple_list_item_1, android.R.id.text1,
                        addList);
                add_list.setAdapter(adapter);

                add_list.setOnItemClickListener(new OnItemClickListener() {

                    @Override
                    public void onItemClick(AdapterView<?> arg0, View arg1,
                                            int arg2, long arg3) {
                        // TODO Auto-generated method stub
                        sipAddress = addList.get(arg2);
                        old_sipAddress = sipAddress;
                        initiateCall();
                        dialog.dismiss();
                    }
                });


                new_add.setOnClickListener(new OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        // TODO Auto-generated method stub
                        showDialog(CALL_ADDRESS);
                        dialog.dismiss();
                    }
                });
                dialog.show();
                break;

            case CALL_ADDRESS:

                LayoutInflater factory = LayoutInflater.from(this);
                final View textBoxView = factory.inflate(
                        R.layout.call_address_dialog, null);
                return new AlertDialog.Builder(this)
                        .setTitle("Call Someone.")
                        .setView(textBoxView)
                        .setPositiveButton(android.R.string.ok,
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog,
                                                        int whichButton) {
                                        EditText textField = (EditText) (textBoxView
                                                .findViewById(R.id.calladdress_edit));
                                        addList.add(textField.getText().toString());

                                        sipAddress = textField.getText().toString();
                                        old_sipAddress = sipAddress;
                                        initiateCall();

                                    }
                                })
                        .setNegativeButton(android.R.string.cancel,
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog,
                                                        int whichButton) {
                                        // Noop.
                                    }
                                }).create();


            case UPDATE_SETTINGS_DIALOG:
                return new AlertDialog.Builder(this)
                        .setMessage("Please update your SIP Account Settings.")
                        .setPositiveButton(android.R.string.ok,
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog,
                                                        int whichButton) {
                                        updatePreferences();
                                    }
                                })
                        .setNegativeButton(android.R.string.cancel,
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog,
                                                        int whichButton) {
                                        // Noop.
                                    }
                                }).create();
        }
        return null;
    }

    public void updatePreferences() {
        Intent settingsActivity = new Intent(getBaseContext(),
                SipSettings.class);
        startActivity(settingsActivity);
    }

    class sipRegistration implements SipRegistrationListener{

        public void onRegistering(String localProfileUri) {
            updateStatus("Registering with SIP Server...");
        }

        public void onRegistrationDone(String localProfileUri,
                                       long expiryTime) {
            updateStatus("Ready");
        }

        public void onRegistrationFailed(
                String localProfileUri, int errorCode,
                String errorMessage) {
            updateStatus("Registration failed.  Please check settings.");
        }

    }



}

