package prova.plugin.boilnfc;

import android.app.Activity;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.nfc.tech.Ndef;
import android.widget.Toast;
import android.content.Intent;
import android.nfc.Tag;
import android.nfc.NdefRecord;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;
import java.io.UnsupportedEncodingException;
import java.io.ByteArrayOutputStream;

import com.nxp.nfclib.ndef.INdefMessage;
import com.nxp.nfclib.ntag.INTag213215216;
import com.nxp.nfclib.ntag.NTagFactory;
import com.nxp.nfclib.CardType;
import com.nxp.nfclib.NxpNfcLib;
import com.nxp.nfclib.utils.Utilities;


public class Module extends ReactContextBaseJavaModule  implements ActivityEventListener, LifecycleEventListener {

  /*private final List<IntentFilter> intentFilters = new ArrayList<IntentFilter>();
  private final ArrayList<String[]> techLists = new ArrayList<String[]>();
  private Context context;
  private ReactApplicationContext reactContext;
  private Boolean isForegroundEnabled = false;
  private Boolean isResumed = false;*/

  private static final String DURATION_SHORT_KEY = "SHORT";
  private static final String DURATION_LONG_KEY = "LONG";
  private NxpNfcLib nxpLib = null;
  private String nxpLibKey = "0235a960fcb9a265be2ffe54da8288bd";
  final byte[] byPassword = new byte[] {(byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff};
  final byte[] byAcknowg = new byte[] {0x00,0x00};
  final byte[] byToWrite = new byte[] {(byte) 0x11,(byte) 0xA2,(byte) 0x1A,(byte) 0xF3};

  public Module(ReactApplicationContext reactContext) {


    super(reactContext);
    reactContext.addActivityEventListener(this);
  }

  @Override
  public void onNewIntent(Intent intent) {
    try {

      Toast.makeText(getReactApplicationContext(), "onNewIntent: ", Toast.LENGTH_LONG).show();

      connect(intent);
    } catch (Exception e) {

        Toast.makeText(getReactApplicationContext(), "NFC connect error: "+e.getMessage(), Toast.LENGTH_LONG).show();

      }
  }
  @Override
  public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {

  }

  @Override
  public String getName() {
    return "NfcProtectedWrite";
  }

  @Override
  public Map<String, Object> getConstants() {
    final Map<String, Object> constants = new HashMap<>();
    constants.put(DURATION_SHORT_KEY, Toast.LENGTH_SHORT);
    constants.put(DURATION_LONG_KEY, Toast.LENGTH_LONG);
    return constants;
  }

  @Override
  public void onHostResume() {
    Toast.makeText(getReactApplicationContext(), "startForeGroundDispatch: ", Toast.LENGTH_LONG).show();

    nxpLib.startForeGroundDispatch();
  }

  @Override
  public void onHostPause() {
    nxpLib.stopForeGroundDispatch();
  }

  @Override
  public void onHostDestroy() {
    nxpLib.stopForeGroundDispatch();
  }


  @ReactMethod
  public void show(String message) {

    Toast.makeText(getReactApplicationContext(), message, Toast.LENGTH_SHORT).show();
  }

  private void initializeLibrary()
  {
    // Initialize the TapLinx library

    nxpLib = NxpNfcLib.getInstance();
    nxpLib.registerActivity(getCurrentActivity(), nxpLibKey);
    nxpLib.startForeGroundDispatch();
    Toast.makeText(getReactApplicationContext(), "initializeLibrary", Toast.LENGTH_LONG).show();


  }

  private NdefMessage createRecord(String content) throws UnsupportedEncodingException {
    byte[] lang = Locale.getDefault().getLanguage().getBytes("UTF-8");
    byte[] text = content.getBytes("UTF-8"); // Content in UTF-8

    int langSize = lang.length;
    int textLength = text.length;

    ByteArrayOutputStream payload = new ByteArrayOutputStream(1 + langSize + textLength);
    payload.write((byte) (langSize & 0x1F));
    payload.write(lang, 0, langSize);
    payload.write(text, 0, textLength);
    NdefRecord record = new NdefRecord(NdefRecord.TNF_WELL_KNOWN,
            NdefRecord.RTD_TEXT, new byte[0],
            payload.toByteArray());

    return new NdefMessage(new NdefRecord[]{record});

  }

  @ReactMethod
  public void start() {
    initializeLibrary();
  }

  @ReactMethod
  public void connect(Intent intent) {

    try{
      byte[] data;
      CardType m_cardType = nxpLib.getCardType( intent );
      Tag tag =  intent.getParcelableExtra( NfcAdapter.EXTRA_TAG );

      INTag213215216 objNtag = NTagFactory.getInstance().getNTAG216(nxpLib.getCustomModules());
      objNtag.getReader().connect();

      NdefMessage message = createRecord("asd");
      Toast.makeText(getReactApplicationContext(), "message: "+message, Toast.LENGTH_LONG).show();
      Ndef ndef = Ndef.get(tag);
      Toast.makeText(getReactApplicationContext(), "ndef: "+ndef, Toast.LENGTH_LONG).show();
      ndef.connect();
      ndef.writeNdefMessage(message);
      ndef.close();
      /*objNtag.enablePasswordProtection(true,0x10);
      Toast.makeText(getReactApplicationContext(), "AUTHENTICATE_1", Toast.LENGTH_LONG).show();*/

//      objNtag.authenticatePwd(byPassword,byAcknowg);




      /*INdefMessage asd = new INdefMessage() {
        @Override
        public byte[] toByteArray() {
          return "asdfasdfasdfasdfasdfasdf".getBytes();
        }
      };

      asd.toByteArray();

      Toast.makeText(getReactApplicationContext(), "read page 4: " + Utilities.byteToHexString(objNtag.read(4)) , Toast.LENGTH_LONG).show();
      objNtag.writeNDEF(asd);
      Toast.makeText(getReactApplicationContext(), "read page 4 after write: " + Utilities.byteToHexString(objNtag.read(4)) , Toast.LENGTH_LONG).show();
*/

      /*byte[] data = objNtag.read(18);
      Toast.makeText(getReactApplicationContext(), "read page 18: " + Utilities.byteToHexString(data) , Toast.LENGTH_LONG).show();
      objNtag.write(18, bytes);
      //objNtag.enablePasswordProtection(true,0x0f);
      //Toast.makeText(getReactApplicationContext(), "enablePasswordProtection: " , Toast.LENGTH_LONG).show();
      data = objNtag.read(18);
      Toast.makeText(getReactApplicationContext(), "read page 18: " + Utilities.byteToHexString(data) , Toast.LENGTH_LONG).show();*/


      //Toast.makeText(getReactApplicationContext(), "FirstUserpage: " + objNtag.getFirstUserpage() , Toast.LENGTH_LONG).show();

      //objNtag.write(0x10, byToWrite);

      /*for (int i = objNtag.getFirstUserpage(); i < objNtag.getFirstUserpage() +36; i++) {
        data = objNtag.read(i);
        Toast.makeText(getReactApplicationContext(), "read page "+i+": " + Utilities.byteToHexString(data) , Toast.LENGTH_LONG).show();
        if(i>15 && i<28 ) {
          objNtag.write(i, bytes);

        }

      }*/

//      Toast.makeText(getReactApplicationContext(), "writed on 0x10: " , Toast.LENGTH_LONG).show();


    } catch (Exception e) {

      Toast.makeText(getReactApplicationContext(), "NFC connect error: "+e.getMessage(), Toast.LENGTH_LONG).show();

    }

  }
}