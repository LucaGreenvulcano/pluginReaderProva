package prova.plugin.boilnfc;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Base64;
import android.util.Log;
import android.provider.Settings;
import com.facebook.react.bridge.*;
import com.facebook.react.modules.core.RCTNativeAppEventEmitter;

import android.app.PendingIntent;
import android.content.IntentFilter.MalformedMimeTypeException;
import android.net.Uri;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcEvent;
import android.nfc.Tag;
import android.nfc.TagLostException;
import android.nfc.tech.TagTechnology;
import android.nfc.tech.Ndef;
import android.nfc.tech.NfcA;
import android.nfc.tech.NdefFormatable;
import android.os.Parcelable;

import org.json.JSONObject;
import org.json.JSONException;

import java.util.*;

import static android.app.Activity.RESULT_OK;
import static android.os.Build.VERSION_CODES.LOLLIPOP;
import static com.facebook.react.bridge.UiThreadUtil.runOnUiThread;

import android.content.pm.PackageManager;

import android.nfc.tech.MifareClassic;
import android.nfc.tech.MifareUltralight;


import android.widget.Toast;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;


//*******************************


import com.nxp.nfclib.CardType;
import com.nxp.nfclib.NxpNfcLib;
import com.nxp.nfclib.desfire.DESFireFactory;
import com.nxp.nfclib.desfire.IDESFireEV1;
import com.nxp.nfclib.exceptions.NxpNfcLibException;
import com.nxp.nfclib.ndef.IType2NdefSupport;
import com.nxp.nfclib.ndef.NdefMessageWrapper;
import com.nxp.nfclib.ndef.NdefRecordWrapper;
import com.nxp.nfclib.ntag.INTag;
import com.nxp.nfclib.ntag.INTag213215216;
import com.nxp.nfclib.ntag.NTag213215216;
import com.nxp.nfclib.ntag.NTagFactory;
import com.nxp.nfclib.utils.NxpLogUtils;
import com.nxp.nfclib.utils.Utilities;

import java.nio.charset.Charset;
import java.util.Locale;


//*******************************


class NfcManager extends ReactContextBaseJavaModule implements ActivityEventListener, LifecycleEventListener {

	//*******

	// private String TAG = MainActivity.class.getSimpleName();
	private String TAG = NfcManager.class.getSimpleName();

	private NxpNfcLib m_libInstance = null;  // The TapLinX library instance
	private final String TAPLINX_KEY = "07698d782c5070246b1df1b1ed79adde";
	//    private final byte[] pwd = new byte[]{0x01, 0x02, 0x03, 0x04};

	private final byte[] pwd = new byte[]{0x31, 0x32, 0x33, 0x34};
	private final byte[] pack = new byte[]{0x00, 0x00};
	private static final String US_ASCII = "US-ASCII";
	private static final String UTF_8 = "UTF-8";

	//*******


	private static final String LOG_TAG = "ReactNativeNfcManager";
    private final List<IntentFilter> intentFilters = new ArrayList<IntentFilter>();
    private final ArrayList<String[]> techLists = new ArrayList<String[]>();
	private Context context;
	private ReactApplicationContext reactContext;
	private Boolean isForegroundEnabled = false;
	private Boolean isResumed = false;
	private WriteNdefRequest writeNdefRequest = null;
	private TagTechnologyRequest techRequest = null;

	//-----
	private Intent ndefIntent = null;

	private void initializeLibrary()
	{                                        // Initialize the TapLinx library
		m_libInstance = NxpNfcLib.getInstance();
		// m_libInstance.registerActivity( this.context, TAPLINX_KEY );
		m_libInstance.registerActivity(getCurrentActivity(), TAPLINX_KEY);
		m_libInstance.startForeGroundDispatch();
		this.show("inizializzato", 1000);

	}

	class WriteNdefRequest {
		NdefMessage message;
		Callback callback;
		boolean format;
		boolean formatReadOnly;

		WriteNdefRequest(NdefMessage message, Callback callback, boolean format, boolean formatReadOnly) {
			this.message = message;
			this.callback = callback;
			this.format = format;
			this.formatReadOnly = formatReadOnly;
		}
	}

    public NfcManager(ReactApplicationContext reactContext) {
        super(reactContext);
        context = reactContext;
        this.reactContext = reactContext;
        reactContext.addActivityEventListener(this);
		reactContext.addLifecycleEventListener(this);
        Log.d(LOG_TAG, "NfcManager created");
		// initializeLibrary();
	}

	@Override
	public String getName() {
		return "Boilerplate";
	}

	private boolean hasPendingRequest() {
		return writeNdefRequest != null || techRequest != null; 
	}


	//----------- TOAST
	@ReactMethod
	public void show(String message, int duration) {
//		String message = "prova toast";
//		int duration = 1000;
		Toast.makeText(getReactApplicationContext(), message, duration).show();
	}

	@ReactMethod
	public void cancelTechnologyRequest(Callback callback) {
		synchronized(this) {
		    if (techRequest != null) {
				techRequest.close();
				techRequest.getPendingCallback().invoke("cancelled");
				techRequest = null;
				callback.invoke();
		    } else {
				// explicitly allow this
				callback.invoke();
			}
		}
	}

	@ReactMethod
	public void requestTechnology(String tech, Callback callback) {
		synchronized(this) {
			if (!isForegroundEnabled) {
				callback.invoke("you should requestTagEvent first");
				return;
			}

		    if (hasPendingRequest()) {
		    	callback.invoke("You can only issue one request at a time");
		    } else {
				techRequest = new TagTechnologyRequest(tech, callback);
			}
		}
	}

	@ReactMethod
	public void closeTechnology(Callback callback) {
		synchronized(this) {
		    if (techRequest != null) {
				techRequest.close();
				techRequest = null;
				callback.invoke();
		    } else {
				// explicitly allow this
				callback.invoke();
			}
		}
	}

	@ReactMethod
	public void getTag(Callback callback) {
		synchronized(this) {
		    if (techRequest != null) {
				try {
				    TagTechnology tagTech = techRequest.getTechHandle();
                    Tag tag = tagTech.getTag();
				    WritableMap parsed = tag2React(tag);
				    callback.invoke(null, parsed);
				} catch (Exception ex) {
					Log.d(LOG_TAG, "getTag fail");
					callback.invoke("getTag fail");
				}
		    } else {
				callback.invoke("no tech request available");
			}
		}
	}

	@ReactMethod
	public void getCachedNdefMessage(Callback callback) {
		synchronized(this) {
		    if (techRequest != null) {
				try {
				    Ndef ndef = (Ndef)techRequest.getTechHandle();
				    WritableMap parsed = ndef2React(ndef, new NdefMessage[] { ndef.getCachedNdefMessage() });
				    callback.invoke(null, parsed);
				} catch (Exception ex) {
					Log.d(LOG_TAG, "getCachedNdefMessage fail");
					callback.invoke("getCachedNdefMessage fail");
				}
		    } else {
				callback.invoke("no tech request available");
			}
		}
	}

	@ReactMethod
	public void getNdefMessage(Callback callback) {
		synchronized(this) {
		    if (techRequest != null) {
				try {
				    Ndef ndef = (Ndef)techRequest.getTechHandle();
				    WritableMap parsed = ndef2React(ndef, new NdefMessage[] { ndef.getNdefMessage() });
				    callback.invoke(null, parsed);
				} catch (Exception ex) {
					Log.d(LOG_TAG, "getNdefMessage fail");
					callback.invoke("getNdefMessage fail");
				}
		    } else {
				callback.invoke("no tech request available");
			}
		}
	}

	@ReactMethod
	public void writeNdefMessage(ReadableArray rnArray, Callback callback) {
		synchronized(this) {
		    if (techRequest != null) {
				try {
				    Ndef ndef = (Ndef)techRequest.getTechHandle();
				    byte[] bytes = rnArrayToBytes(rnArray);
				    ndef.writeNdefMessage(new NdefMessage(bytes));
				    callback.invoke();
				} catch (Exception ex) {
					Log.d(LOG_TAG, "writeNdefMessage fail");
					callback.invoke("writeNdefMessage fail");
				}
		    } else {
				callback.invoke("no tech request available");
			}
		}
	}

	@ReactMethod
	public void makeReadOnly(Callback callback) {
		synchronized(this) {
		    if (techRequest != null) {
				try {
				    Ndef ndef = (Ndef)techRequest.getTechHandle();
					boolean result = ndef.makeReadOnly();
				    callback.invoke(null, result);
				} catch (Exception ex) {
					Log.d(LOG_TAG, "makeReadOnly fail");
					callback.invoke("makeReadOnly fail");
				}
		    } else {
				callback.invoke("no tech request available");
			}
		}
	}

	@ReactMethod
	public void transceive(ReadableArray rnArray, Callback callback) {
		synchronized(this) {
		    if (techRequest != null) {
				try {
					String tech = techRequest.getTechType();
				    byte[] bytes = rnArrayToBytes(rnArray);
					if (tech.equals("NfcA")) {
						NfcA techHandle = (NfcA)techRequest.getTechHandle();
				    	byte[] resultBytes = techHandle.transceive(bytes);
						WritableArray resultRnArray = bytesToRnArray(resultBytes);
				    	callback.invoke(null, resultRnArray);
						return;
					}
				} catch (Exception ex) {
					Log.d(LOG_TAG, "transceive fail");
				}

				callback.invoke("transceive fail");
		    } else {
				callback.invoke("no tech request available");
			}
		}
	}

	@ReactMethod
	public void cancelNdefWrite(Callback callback) {
		synchronized(this) {
		    if (writeNdefRequest != null) {
		    	writeNdefRequest.callback.invoke("cancelled");
				writeNdefRequest = null;
				callback.invoke();
		    } else {
				callback.invoke("no writing request available");
			}
		}
	}

	@ReactMethod
	public void requestNdefWrite(ReadableArray rnArray, ReadableMap options, Callback callback) {
		synchronized(this) {
			if (!isForegroundEnabled) {
				callback.invoke("you should requestTagEvent first");
				return;
			}

		    if (hasPendingRequest()) {
		    	callback.invoke("You can only issue one request at a time");
		    } else {
				boolean format = options.getBoolean("format");
				boolean formatReadOnly = options.getBoolean("formatReadOnly");
				
		        try {
					NdefMessage msgToWrite;

					/// the only case we allow ndef message to be null is when formatting, see:
					/// https://developer.android.com/reference/android/nfc/tech/NdefFormatable.html#format(android.nfc.NdefMessage)
					///	this API allows the `firstMessage` to be null
					if (format && rnArray == null) {
						msgToWrite = null;
					} else {
						byte[] bytes = rnArrayToBytes(rnArray);
						msgToWrite = new NdefMessage(bytes);
					}

		    		writeNdefRequest = new WriteNdefRequest(
						msgToWrite,
						callback, // defer the callback 
						format,
						formatReadOnly
					); 
		        } catch (FormatException e) {
		        	callback.invoke("Incorrect ndef format");
		        }
		    }
		}
	}


	//***********************
	@ReactMethod
	private void writeNXP(Tag tag, WriteNdefRequest request)
	{
		NdefMessage message = request.message;
		Callback callback = request.callback;
		boolean formatReadOnly = request.formatReadOnly;
		boolean format = request.format;

		if (format || formatReadOnly) {
			try {
				Log.d(LOG_TAG, "ready to writeNdef");
				NdefFormatable formatable = NdefFormatable.get(tag);
				if (formatable == null) {
					callback.invoke("fail to apply ndef formatable tech");
				} else {
					Log.d(LOG_TAG, "ready to format ndef, seriously");
					formatable.connect();
					if (formatReadOnly) {
						formatable.formatReadOnly(message);
					} else {
						formatable.format(message);
					}
					callback.invoke();
				}
			} catch (Exception ex) {
				callback.invoke(ex.getMessage());
			}
		} else {
			try {
				Log.d(LOG_TAG, "ready to writeNdef");
				Ndef ndef = Ndef.get(tag);
				if (ndef == null) {
					callback.invoke("fail to apply ndef tech");
				} else if (!ndef.isWritable()) {
					callback.invoke("tag is not writeable");
				} else if (ndef.getMaxSize() < message.toByteArray().length) {
					callback.invoke("tag size is not enough");
				} else {
					Log.d(LOG_TAG, "ready to writeNdef, seriously");
					ndef.connect();
					ndef.writeNdefMessage(message);
					callback.invoke();
				}
			} catch (Exception ex) {
				callback.invoke(ex.getMessage());
			}
		}


		/*
		try
		{
		//	CardType cardType = m_libInstance.getCardType( intent );
		//	if( CardType.NTag213 == cardType )
		//	{


				NTag213215216 tag = (NTag213215216)NTagFactory.getInstance().getNTAG213( m_libInstance.getCustomModules() );
				Log.d( TAG, "Connecting...");
				tag.getReader().connect();
				Log.d( TAG, "Card Detected: "+tag.getType().getTagName()+"\n\n");

				String msg = "123BRAND|BRAND CODE|MODEL|SIZE";
				NdefMessageWrapper ndefMW = new NdefMessageWrapper(createTextRecord(msg, Locale.ITALY, true));

				try {
					Log.d( TAG, "FirstUserpage: "+tag.getFirstUserpage());
					Log.d( TAG, "LastUserPage: "+tag.getLastUserPage());

					Log.d( TAG, "FirstPageLocked? : "+tag.isPageLocked(tag.getFirstUserpage()));
					Log.d( TAG, "LastPageLocked? : "+tag.isPageLocked(tag.getLastUserPage()));

					if(tag.isPageLocked(tag.getFirstUserpage())){
						Log.d( TAG, "Authenticating...");
						tag.authenticatePwd(pwd, pack);

						if(tag.isPwdAuthenticated()){
							Log.d( TAG, "Authenticated");

							tag.clear();
							Log.d( TAG, "tag cleared");

							Log.d( TAG, "Writing "+msg);
							tag.writeNDEF(tag1, request);
							//tag.writeNDEF(ndefMW);

							tag.getReader().close();
							Log.d( TAG, "DONE!");
						}
						else{
							Log.d( TAG, "NOT Authenticated!!!!");
						}
					}
					else{
						tag.clear();
						Log.d( TAG, "tag cleared");

						Log.d( TAG, "Writing "+msg);
						tag.writeNDEF(ndefMW);

						tag.getReader().close();
						Log.d( TAG, "DONE!");
					}

					Log.d( TAG, "Protecting...");
					tag.setMemProtectionAndPwdVerificationForReadWriteAccess((byte)tag.getFirstUserpage(), false);
//
					Log.d( TAG, "setting Pwd and Pack...");
					tag.programPWDPack(pwd, pack);

					tag.getReader().close();
					Log.d( TAG, "DONE!");

					byte[] aArr = new byte[]{0x01, 0x02, 0x03, 0x04};
					String a = new String(aArr);
					Log.d( TAG, "a: "+a);

					byte[] bArr = new byte[]{0x31, 0x32, 0x33, 0x34};
					String b = new String(bArr);
					Log.d( TAG, "b: "+b);


//                    NxpLogUtils.save();
				} catch (Exception e) {
					Log.d( TAG, e.getMessage());
//                    NxpLogUtils.save();
				}
		//	}
		}
		catch( Throwable t )
		{
			t.printStackTrace();
		}*/
	}


	@ReactMethod
	public void writePassword(Intent intent){
		// this.writeNXP(intent);
		//this.writeNXP(ndefIntent);
	}


	//***********************



	@ReactMethod
	public void start(Callback callback) {

		NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(context);
		if (nfcAdapter != null) {
			Log.d(LOG_TAG, "start");

			IntentFilter filter = new IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED);
			Activity currentActivity = getCurrentActivity();
		    if (currentActivity == null) {
		    	callback.invoke("fail to get current activity");
		    	return;
		    }

			currentActivity.registerReceiver(mReceiver, filter);
			callback.invoke();
		} else {
			Log.d(LOG_TAG, "not support in this device");
			callback.invoke("no nfc support");
		}

		initializeLibrary();
	}




	@ReactMethod
	public void requestNdefProtectWrite(ReadableArray rnArray, ReadableArray password, ReadableMap options, Callback callback ) {

		synchronized(this) {
			if (!isForegroundEnabled) {
				callback.invoke("you should requestTagEvent first");
				return;
			}

		    if (hasPendingRequest()) {
		    	callback.invoke("You can only issue one request at a time");
		    } else {
				boolean format = options.getBoolean("format");
				boolean formatReadOnly = options.getBoolean("formatReadOnly");

		        try {
					NdefMessage msgToWrite;

					/// the only case we allow ndef message to be null is when formatting, see:
					/// https://developer.android.com/reference/android/nfc/tech/NdefFormatable.html#format(android.nfc.NdefMessage)
					///	this API allows the `firstMessage` to be null
					if (format && rnArray == null) {
						msgToWrite = null;
					} else {
						byte[] bytes = rnArrayToBytes(rnArray);
						msgToWrite = new NdefMessage(bytes);
					}

		    		writeNdefRequest = new WriteNdefRequest(
						msgToWrite,
						callback, // defer the callback
						format,
						formatReadOnly
					);
		        } catch (FormatException e) {
		        	callback.invoke("Incorrect ndef format");
		        }
		    }
		}
	}


	@ReactMethod
	public void setNdefPushMessage(ReadableArray rnArray, Callback callback) {
		synchronized(this) {
		    if (techRequest == null && writeNdefRequest == null) {
				try {
        			Activity currentActivity = getCurrentActivity();
					if (currentActivity == null) {
						throw new RuntimeException("cannot get current activity");
					}

					NdefMessage msgToPush = null;
					if (rnArray != null) {
						msgToPush = new NdefMessage(rnArrayToBytes(rnArray));
					}

					NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(context);
					nfcAdapter.setNdefPushMessage(msgToPush, currentActivity);
				    callback.invoke();
				} catch (Exception ex) {
					Log.d(LOG_TAG, "sendNdefPushMessage fail, " + ex.getMessage());
					callback.invoke("sendNdefPushMessage fail");
				}
		    } else {
				callback.invoke("please first cancel existing tech or write request");
			}
		}
	}





   	@ReactMethod
	public void isSupported(Callback callback){
		Log.d(LOG_TAG, "isSupported");
        Activity currentActivity = getCurrentActivity();
		if (currentActivity == null) {
			callback.invoke("fail to get current activity");
			return;
		}

		boolean result = currentActivity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_NFC);
		callback.invoke(null, result);
	}

	@ReactMethod
	public void isEnabled(Callback callback) {
		Log.d(LOG_TAG, "isEnabled");
        NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(context);
		if (nfcAdapter != null) {
			callback.invoke(null, nfcAdapter.isEnabled());
		} else {
			callback.invoke(null, false);
		}
	}

	@ReactMethod
	public void goToNfcSetting(Callback callback) {
		Log.d(LOG_TAG, "goToNfcSetting");
        Activity currentActivity = getCurrentActivity();
		if (currentActivity == null) {
			callback.invoke("fail to get current activity");
			return;
		}

		currentActivity.startActivity(new Intent(Settings.ACTION_NFC_SETTINGS));
		callback.invoke();
	}

	@ReactMethod
	public void getLaunchTagEvent(Callback callback) {
        Activity currentActivity = getCurrentActivity();
		if (currentActivity == null) {
			callback.invoke("fail to get current activity");
			return;
		}

		Intent launchIntent = currentActivity.getIntent();
		WritableMap nfcTag = parseNfcIntent(launchIntent);
		callback.invoke(null, nfcTag);
	}

	@ReactMethod
    private void registerTagEvent(String alertMessage, Boolean invalidateAfterFirstRead, Callback callback) {
        Log.d(LOG_TAG, "registerTag");
		isForegroundEnabled = true;

		// capture all mime-based dispatch NDEF
		IntentFilter ndef = new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED);
		Activity currentActivity = getCurrentActivity();
		ndefIntent = currentActivity.getIntent();

		try {
			ndef.addDataType("* /*");
		} catch (MalformedMimeTypeException e) {
			throw new RuntimeException("fail", e);
	    }
		intentFilters.add(ndef);

		// capture all rest NDEF, such as uri-based
        intentFilters.add(new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED));
		techLists.add(new String[]{Ndef.class.getName()});

		// for those without NDEF, get them as tags
        intentFilters.add(new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED));

		if (isResumed) {
			enableDisableForegroundDispatch(true);
		}
        callback.invoke();
  	}

	@ReactMethod
    private void unregisterTagEvent(Callback callback) {
        Log.d(LOG_TAG, "registerTag");
		isForegroundEnabled = false;
		intentFilters.clear();
		if (isResumed) {
			enableDisableForegroundDispatch(false);
		}
        callback.invoke();
  	}

    @Override
    public void onHostResume() {
        Log.d(LOG_TAG, "onResume");
		isResumed = true;
		if (isForegroundEnabled) {
			enableDisableForegroundDispatch(true);
		}
    }

    @Override
    public void onHostPause() {
        Log.d(LOG_TAG, "onPause");
		isResumed = false;
		enableDisableForegroundDispatch(false);
    }

    @Override
    public void onHostDestroy() {
        Log.d(LOG_TAG, "onDestroy");
    }

    private void enableDisableForegroundDispatch(boolean enable) {
        Log.i(LOG_TAG, "enableForegroundDispatch, enable = " + enable);
        NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(context);
        Activity currentActivity = getCurrentActivity();

        if (nfcAdapter != null && currentActivity != null && !currentActivity.isFinishing()) {
            try {
				if (enable) {
                    nfcAdapter.enableForegroundDispatch(currentActivity, getPendingIntent(), getIntentFilters(), getTechLists());
				} else {
					nfcAdapter.disableForegroundDispatch(currentActivity);
				}
            } catch (IllegalStateException | NullPointerException e) {
                Log.w(LOG_TAG, "Illegal State Exception starting NFC. Assuming application is terminating.");
            }
        }
    }

    private PendingIntent getPendingIntent() {
        Activity activity = getCurrentActivity();
        Intent intent = new Intent(activity, activity.getClass());
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(activity, 0, intent, 0);
    }

    private IntentFilter[] getIntentFilters() {
        return intentFilters.toArray(new IntentFilter[intentFilters.size()]);
    }

    private String[][] getTechLists() {
        return techLists.toArray(new String[0][0]);
    }


	private void sendEvent(String eventName,
						   @Nullable WritableMap params) {
		getReactApplicationContext()
				.getJSModule(RCTNativeAppEventEmitter.class)
				.emit(eventName, params);
	}


	private void sendEventWithJson(String eventName,
						  JSONObject json) {
		try {
			WritableMap map = JsonConvert.jsonToReact(json);
			sendEvent(eventName, map);
		} catch (JSONException ex) {
			Log.d(LOG_TAG, "fireNdefEvent fail: " + ex);
		}
	}



	private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			Log.d(LOG_TAG, "onReceive " + intent);
			final String action = intent.getAction();

            if (action.equals(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED)) {
                final int state = intent.getIntExtra(NfcAdapter.EXTRA_ADAPTER_STATE,
                                                     NfcAdapter.STATE_OFF);
				String stateStr = "unknown";
                switch (state) {
                case NfcAdapter.STATE_OFF:
					stateStr = "off";
                    break;
                case NfcAdapter.STATE_TURNING_OFF:
					stateStr = "turning_off";
                    break;
                case NfcAdapter.STATE_ON:
					stateStr = "on";
                    break;
                case NfcAdapter.STATE_TURNING_ON:
					stateStr = "turning_on";
                    break;
                }

                try {
        			WritableMap writableMap = Arguments.createMap();
					writableMap.putString("state", stateStr);
                    sendEvent("NfcManagerStateChanged", writableMap);
                } catch (Exception ex) {
                    Log.d(LOG_TAG, "send nfc state change event fail: " + ex);
                }
            }
		}
	};

	@Override
	public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
		Log.d(LOG_TAG, "onActivityResult");
	}



	@Override
	public void onNewIntent(Intent intent) {
        Log.d(LOG_TAG, "onNewIntent " + intent);
		WritableMap nfcTag = parseNfcIntent(intent);
		if (nfcTag != null) {
			sendEvent("NfcManagerDiscoverTag", nfcTag);
		}
	}


	private WritableMap parseNfcIntent(Intent intent) {
		Log.d(LOG_TAG, "parseIntent " + intent);
		String action = intent.getAction();
		Log.d(LOG_TAG, "action " + action);
		if (action == null) {
			return null;
		}

		WritableMap parsed = null;
		Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
		// Parcelable[] messages = intent.getParcelableArrayExtra((NfcAdapter.EXTRA_NDEF_MESSAGES));

		synchronized(this) {
			if (writeNdefRequest != null) {
				writeNXP( //writeNdef(
					tag, 
					writeNdefRequest
				);
				writeNdefRequest = null;

				// explicitly return null, to avoid extra detection
				return null;
			} else if (techRequest != null) {
				if (!techRequest.isConnected()) {
					boolean result = techRequest.connect(tag);
					if (result) {
						techRequest.getPendingCallback().invoke();
					} else {
						techRequest.getPendingCallback().invoke("fail to connect tag");
						techRequest = null;
					}
				}

				// explicitly return null, to avoid extra detection
				return null;
			}
		}

		if (action.equals(NfcAdapter.ACTION_NDEF_DISCOVERED)) {
			Ndef ndef = Ndef.get(tag);
			Parcelable[] messages = intent.getParcelableArrayExtra((NfcAdapter.EXTRA_NDEF_MESSAGES));
			parsed = ndef2React(ndef, messages);
		} else if (action.equals(NfcAdapter.ACTION_TECH_DISCOVERED)) {
			for (String tagTech : tag.getTechList()) {
				Log.d(LOG_TAG, tagTech);
				if (tagTech.equals(NdefFormatable.class.getName())) {
					// fireNdefFormatableEvent(tag);
					parsed = tag2React(tag);
				} else if (tagTech.equals(Ndef.class.getName())) { //
					Ndef ndef = Ndef.get(tag);
					parsed = ndef2React(ndef, new NdefMessage[] { ndef.getCachedNdefMessage() });
				}
			}
		} else if (action.equals(NfcAdapter.ACTION_TAG_DISCOVERED)) {
			parsed = tag2React(tag);
		}

		return parsed;
	}


	private WritableMap tag2React(Tag tag) {
		try {
			JSONObject json = Util.tagToJSON(tag);
			return JsonConvert.jsonToReact(json);
		} catch (JSONException ex) {
			return null;
		}
	}

    private WritableMap ndef2React(Ndef ndef, Parcelable[] messages) {
		try {
			JSONObject json = buildNdefJSON(ndef, messages);
			return JsonConvert.jsonToReact(json);
		} catch (JSONException ex) {
			return null;
		}
    }


	private void writeNdef(Tag tag, WriteNdefRequest request) {
		NdefMessage message = request.message; 
		Callback callback = request.callback;
		boolean formatReadOnly = request.formatReadOnly;
		boolean format = request.format;

		if (format || formatReadOnly) {
	    	try {
                Log.d(LOG_TAG, "ready to writeNdef");
	    		NdefFormatable formatable = NdefFormatable.get(tag);
	    		if (formatable == null) {
	    			callback.invoke("fail to apply ndef formatable tech");
               } else {
                	Log.d(LOG_TAG, "ready to format ndef, seriously");
	    			formatable.connect();
					if (formatReadOnly) {
						formatable.formatReadOnly(message);
					} else {
						formatable.format(message);
					}
	    			callback.invoke();
	    		}
	    	} catch (Exception ex) {
	    		callback.invoke(ex.getMessage());
	    	}
		} else {
	    	try {
                Log.d(LOG_TAG, "ready to writeNdef");
	    		Ndef ndef = Ndef.get(tag);
	    		if (ndef == null) {
	    			callback.invoke("fail to apply ndef tech");
	    		} else if (!ndef.isWritable()) {
	    			callback.invoke("tag is not writeable");
                } else if (ndef.getMaxSize() < message.toByteArray().length) {
	    			callback.invoke("tag size is not enough");
                } else {
                	Log.d(LOG_TAG, "ready to writeNdef, seriously");
	    			ndef.connect();
	    			ndef.writeNdefMessage(message);
	    			callback.invoke();
	    		}
	    	} catch (Exception ex) {
	    		callback.invoke(ex.getMessage());
	    	}
		}
	}



	JSONObject buildNdefJSON(Ndef ndef, Parcelable[] messages) {
		JSONObject json = Util.ndefToJSON(ndef);

		// ndef is null for peer-to-peer
		// ndef and messages are null for ndef format-able
		if (ndef == null && messages != null) {
			try {

				if (messages.length > 0) {
					NdefMessage message = (NdefMessage) messages[0];
					json.put("ndefMessage", Util.messageToJSON(message));
					// guessing type, would prefer a more definitive way to determine type
					json.put("type", "NDEF Push Protocol");
				}

				if (messages.length > 1) {
					Log.d(LOG_TAG, "Expected one ndefMessage but found " + messages.length);
				}

			} catch (JSONException e) {
				// shouldn't happen
				Log.e(Util.TAG, "Failed to convert ndefMessage into json", e);
			}
		}
		return json;
	}




	private byte[] rnArrayToBytes(ReadableArray rArray) {
		byte[] bytes = new byte[rArray.size()];
		for (int i = 0; i < rArray.size(); i++) {
			bytes[i] = (byte)(rArray.getInt(i) & 0xff);
		}
		return bytes;
	}

	public WritableArray bytesToRnArray(byte[] bytes) {
        WritableArray value = Arguments.createArray();
        for (int i = 0; i < bytes.length; i++) {
            value.pushInt((bytes[i] & 0xFF));
		}
        return value;
    }

	public NdefRecordWrapper createTextRecord(String payload, Locale locale, boolean encodeInUtf8) {
		byte[] langBytes = locale.getLanguage().getBytes(Charset.forName(UTF_8));
		Charset utfEncoding = encodeInUtf8 ? Charset.forName(UTF_8) : Charset.forName("UTF-16");
		byte[] textBytes = payload.getBytes(utfEncoding);
		int utfBit = encodeInUtf8 ? 0 : (1 << 7);
		char status = (char) (utfBit + langBytes.length);
		byte[] data = new byte[1 + langBytes.length + textBytes.length];
		data[0] = (byte) status;
		System.arraycopy(langBytes, 0, data, 1, langBytes.length);
		System.arraycopy(textBytes, 0, data, 1 + langBytes.length, textBytes.length);
		return new NdefRecordWrapper(NdefRecord.TNF_WELL_KNOWN, NdefRecord.RTD_TEXT, new byte[0], data);
	}
}

