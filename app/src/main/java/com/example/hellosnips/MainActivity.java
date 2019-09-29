/**
 *  Author: Mengyuan Chao
 *  Date: 2019/09/28
 *  Acknowledge to: Snips
 * */

package com.example.hellosnips;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import ai.snips.hermes.IntentMessage;
import ai.snips.platform.SnipsPlatformClient;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;

import android.os.Environment;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class MainActivity extends AppCompatActivity implements LocationListener {

    private File assistantLocation;

    private static final String TAG = "MainActivity";

    private TextView mLog = null;

    LocationManager locationManager = null;

    String currentLocation = "Blocker Building, 155 Ireland Street, TAMU (Indoor).";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        mLog = (TextView) findViewById(R.id.mlog);
        assistantLocation = new File(getFilesDir(), "snips");
        extractAssistantIfNeeded(assistantLocation);

        if (ensurePermissions()) {
            startSnips(assistantLocation);
            startGPS();
            copyAssets();
        }


    }

    private void extractAssistantIfNeeded(File assistantLocation) {
        File versionFile = new File(assistantLocation,
                "android_version_" + BuildConfig.VERSION_NAME);

        if (versionFile.exists()) {
            return;
        }

        try {
            assistantLocation.delete();
            MainActivity.unzip(getBaseContext().getAssets().open("assistant.zip"),
                    assistantLocation);
            versionFile.createNewFile();
        } catch (IOException e) {
            return;
        }
    }

    private static void unzip(InputStream zipFile, File targetDirectory)
            throws IOException {
        ZipInputStream zis = new ZipInputStream(new BufferedInputStream(zipFile));
        try {
            ZipEntry ze;
            int count;
            byte[] buffer = new byte[8192];
            while ((ze = zis.getNextEntry()) != null) {
                File file = new File(targetDirectory, ze.getName());
                File dir = ze.isDirectory() ? file : file.getParentFile();
                if (!dir.isDirectory() && !dir.mkdirs())
                    throw new FileNotFoundException("Failed to ensure directory: " +
                            dir.getAbsolutePath());
                if (ze.isDirectory())
                    continue;
                FileOutputStream fout = new FileOutputStream(file);
                try {
                    while ((count = zis.read(buffer)) != -1)
                        fout.write(buffer, 0, count);
                } finally {
                    fout.close();
                }
            }
        } finally {
            zis.close();
        }
    }

    private boolean ensurePermissions() {
        int status = ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                + ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                + ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                + ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (status != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.READ_EXTERNAL_STORAGE}, 0);
            return false;
        }
        Log.d(TAG, "Allow recording audio.");
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 0 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
        {
            startSnips(assistantLocation);
            startGPS();
            copyAssets();
        }
        else if(requestCode == 101 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
        {
            callPhoneNumber();
        }
        else if(requestCode == 102 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
        {
            sendMessage();
        }
    }

    private SnipsPlatformClient createClient(File assistantLocation) {
        File assistantDir  = new File(assistantLocation, "assistant");

        final SnipsPlatformClient client = new SnipsPlatformClient.Builder(assistantDir)
                .enableDialogue(true)
                .enableHotword(true)
                .enableSnipsWatchHtml(false)
                .enableLogs(true)
                .withHotwordSensitivity(0.5f)
                .enableStreaming(false)
                .enableInjection(false)
                .build();

        client.setOnPlatformReady(new Function0<Unit>() {
            @Override
            public Unit invoke() {
                Log.d(TAG, "Snips is ready. Say the wake word!");
                return null;
            }
        });

        client.setOnPlatformError(
                new Function1<SnipsPlatformClient.SnipsPlatformError, Unit>() {
                    @Override
                    public Unit invoke(final SnipsPlatformClient.SnipsPlatformError
                                               snipsPlatformError) {
                        // Handle error
                        Log.d(TAG, "Error: " + snipsPlatformError.getMessage());
                        return null;
                    }
                });

        client.setOnHotwordDetectedListener(new Function0<Unit>() {
            @Override
            public Unit invoke() {
                // Wake word detected, start a dialog session
                Log.d(TAG, "Wake word detected!");
                client.startSession(null, new ArrayList<String>(),
                        false, null);
                return null;
            }
        });

        client.setOnIntentDetectedListener(new Function1<IntentMessage, Unit>() {
            @Override
            public Unit invoke(final IntentMessage intentMessage) {
                // Intent detected, so the dialog session ends here
                client.endSession(intentMessage.getSessionId(), null);
                String intentName = intentMessage.getIntent().getIntentName().split("\\:")[1];
                mLog.setText(intentName);
                Log.d(TAG, "Intent detected: " + intentName);
                triggerActivity(intentName);
                return null;
            }
        });

        client.setOnSnipsWatchListener(new Function1<String, Unit>() {
            public Unit invoke(final String s) {
                Log.d(TAG, "Log: " + s);
                return null;
            }
        });

        return client;
    }

    private void startSnips(File snipsDir) {
        SnipsPlatformClient client = createClient(snipsDir);
        client.connect(this.getApplicationContext());
    }

    private void startGPS(){
        if(ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 1, MainActivity.this);
            Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (location!=null)
                currentLocation = "GPS Location: " + "(" + location.getLatitude() + " , " + location.getLongitude() + ")";
        }
    }

    private void copyAssets(){
        CopyAssets.copyAssets(this);
    }

    private void triggerActivity(String avtivity){
        switch(avtivity){
            case "callPhone":
                callPhoneNumber();
                break;
            case "sendSMS":
                sendMessage();
                break;
            case "sendEmail":
                sendEmail();
                break;
            case "tellLocation":
                mLog.setText(currentLocation());
                break;
        }
    }

    private void callPhoneNumber()
    {
        BufferedReader reader;
        String mobileNumber = "5515870783";
        try{
            File mobileNumberFile = new File(Environment.getExternalStorageDirectory() + "/helloSnips/" + "mobileNumber.conf");
            final InputStream file = new FileInputStream(mobileNumberFile);
            reader = new BufferedReader(new InputStreamReader(file));
            mobileNumber = reader.readLine();
        } catch(IOException ioe){
            ioe.printStackTrace();
        }

        try
        {
            if(Build.VERSION.SDK_INT > 22)
            {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CALL_PHONE}, 101);
                    return;
                }

                Intent callIntent = new Intent(Intent.ACTION_CALL);
                callIntent.setData(Uri.parse(mobileNumber));
                startActivity(callIntent);

            }
            else {
                Intent callIntent = new Intent(Intent.ACTION_CALL);
                callIntent.setData(Uri.parse(mobileNumber));
                startActivity(callIntent);
            }
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }
    }

    private String currentTime()
    {
        DateFormat df = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss z");
        String date = df.format(Calendar.getInstance().getTime());
        return date;
    }

    private String currentLocation(){
        return currentLocation;
    }

    private void sendMessage(){
        BufferedReader reader;
        String mobileNumber = "5515870783";
        try{
            File mobileNumberFile = new File(Environment.getExternalStorageDirectory() + "/helloSnips/" + "mobileNumber.conf");
            final InputStream file = new FileInputStream(mobileNumberFile);
            reader = new BufferedReader(new InputStreamReader(file));
            mobileNumber = reader.readLine().split("\\:")[1];
        } catch(IOException ioe){
            ioe.printStackTrace();
        }

        try
        {
            if(Build.VERSION.SDK_INT > 22)
            {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.SEND_SMS}, 102);
                    return;
                }

                try{
                    SmsManager smgr = SmsManager.getDefault();
                    String smsMessage = "Hi, this is Mengyuan. I am now ( "+ currentTime() + " ) at "+ currentLocation() + " Please reply your current location.";
                    smgr.sendTextMessage(mobileNumber,null,smsMessage,null,null);
                    Toast.makeText(MainActivity.this, "SMS Sent Successfully", Toast.LENGTH_SHORT).show();
                }
                catch (Exception e){
                    Toast.makeText(MainActivity.this, "SMS Failed to Send, Please try again", Toast.LENGTH_SHORT).show();
                    e.printStackTrace();
                }

            }
            else {
                try{
                    SmsManager smgr = SmsManager.getDefault();
                    String smsMessage = "Hi, this is Mengyuan. I am now ( "+ currentTime() + " ) at "+ currentLocation() + " Please reply your current location.";
                    smgr.sendTextMessage(mobileNumber,null,smsMessage,null,null);
                    Toast.makeText(MainActivity.this, "SMS Sent Successfully", Toast.LENGTH_SHORT).show();
                }
                catch (Exception e){
                    Toast.makeText(MainActivity.this, "SMS Failed to Send, Please try again", Toast.LENGTH_SHORT).show();
                    e.printStackTrace();
                }
            }
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }

    }

    private void sendEmail(){
        List<String> groupEmailList = new ArrayList<>();

        BufferedReader reader;
        try{
            File mobileNumberFile = new File(Environment.getExternalStorageDirectory() + "/helloSnips/" + "emailAddress.conf");
            final InputStream file = new FileInputStream(mobileNumberFile);
            reader = new BufferedReader(new InputStreamReader(file));
            String emailAddress = reader.readLine();
            while(emailAddress != null){
                groupEmailList.add(emailAddress);
                emailAddress = reader.readLine();
            }
        } catch(IOException ioe){
            ioe.printStackTrace();
        }

        Intent it = new Intent(Intent.ACTION_SEND);
        String[] emailList = groupEmailList.toArray(new String[0]);
        String emailSubject = "This is a Tech for Protect Hackthon";
        String emailMessage = "Hi, this is Mengyuan. I am now ( "+ currentTime() + " ) at "+ currentLocation() + " Please reply your current location.";
        it.putExtra(Intent.EXTRA_EMAIL, emailList);
        it.putExtra(Intent.EXTRA_SUBJECT,emailSubject);
        it.putExtra(Intent.EXTRA_TEXT,emailMessage);
        it.setType("message/rfc822");
        startActivity(Intent.createChooser(it,"Choose Mail App"));
    }

    @Override
    public void onLocationChanged(Location location) {
        if (location!=null)
            currentLocation = "GPS Location: " + "("+location.getLatitude() +" , " + location.getLongitude() + ")";
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }
}
