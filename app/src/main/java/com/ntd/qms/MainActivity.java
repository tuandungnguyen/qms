package com.ntd.qms;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;

import android.content.Intent;
import android.os.Bundle;
import android.view.WindowManager;

public class MainActivity extends AppCompatActivity implements FragmentManager.OnBackStackChangedListener {

    public static final String MY_PREFS_NAME = "shared_pref";
    public static final String KEY_DEVICE_ID = "key_device_id";
    public static final String KEY_ROOM_NAME = "key_room_name";
    public static final String KEY_AREA_NAME = "key_area_name";
    public static final String KEY_LINE_NUMBER = "key_line_number";
    public static final String KEY_COLUMN_NUMBER = "key_column_number";
    public static final String KEY_BAUD_RATE = "key_baud_rate";

    public static final String USB_DEVICE = "device";
    public static final String USB_PORT = "port";
    public static final String USB_BAUD_RATE = "baud";
    public static final String USB_IO_MANAGER = "withIoManager";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        getSupportFragmentManager().addOnBackStackChangedListener(this);
        if (savedInstanceState == null)
            getSupportFragmentManager().beginTransaction().add(R.id.fragment, new ConfigFragment(), "devices").commit();
        else
            onBackStackChanged();
    }

    @Override
    public void onBackStackChanged() {
        // getSupportActionBar().setDisplayHomeAsUpEnabled(getSupportFragmentManager().getBackStackEntryCount()>0);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        if(intent.getAction().equals("android.hardware.usb.action.USB_DEVICE_ATTACHED")) {
            TerminalFragment terminal = (TerminalFragment)getSupportFragmentManager().findFragmentByTag("terminal");
            if (terminal != null)
                terminal.status("USB device detected");
        }
        super.onNewIntent(intent);
    }

}
