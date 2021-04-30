package com.ntd.qms;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.ntd.qms.adapter.DeviceAdapter;
import com.ntd.qms.data.DeviceItem;
import com.ntd.qms.databinding.FragmentConfigBinding;

import java.util.ArrayList;

import static android.content.Context.MODE_PRIVATE;

public class ConfigFragment extends Fragment implements DeviceAdapter.ClickListener{

    private FragmentConfigBinding binding;

    private ArrayList<DeviceItem> listItems = new ArrayList<>();
    private DeviceAdapter deviceAdapter;
    private int baudRate = 38400;
    private boolean withIoManager = true;
    private int maxLines = 1; //Default = 1 line
    private int maxColumns = 1; //Default = 1 column
    private int typeRoom = 0; //Default = 0

    SharedPreferences prefs;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = DataBindingUtil.inflate(
                inflater, R.layout.fragment_config, container, false);

        prefs = getActivity().getSharedPreferences(MainActivity.MY_PREFS_NAME, MODE_PRIVATE);

        binding.edtAndroidBoxID.setText("" + prefs.getInt(MainActivity.KEY_DEVICE_ID, 1));
        binding.edtRoom.setText(prefs.getString(MainActivity.KEY_ROOM_NAME, ""));
        binding.edtArea.setText(prefs.getString(MainActivity.KEY_PLACE_NAME, ""));

        baudRate = prefs.getInt(MainActivity.KEY_BAUD_RATE, 38400);
        maxLines =  prefs.getInt(MainActivity.KEY_LINE_NUMBER, 1);
        maxColumns = prefs.getInt(MainActivity.KEY_COLUMN_NUMBER, 1);
        typeRoom = prefs.getInt(MainActivity.KEY_ROOM_TYPE, 0);

        deviceAdapter = new DeviceAdapter(getActivity(), this);

        final String[] valuesBR = getResources().getStringArray(R.array.baud_rates);
        int posBR = java.util.Arrays.asList(valuesBR).indexOf(String.valueOf(baudRate));
        binding.spinnerBaudRate.setAdapter(new ArrayAdapter<>(getActivity(), R.layout.spinner_item, valuesBR));
        binding.spinnerBaudRate.setSelection(posBR, true);
        binding.spinnerBaudRate.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id) {
                baudRate = Integer.parseInt(valuesBR[pos]);
                SharedPreferences.Editor editor = prefs.edit();
                editor.putInt(MainActivity.KEY_BAUD_RATE, baudRate);
                editor.apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        final String[] valuesMaxLines = getResources().getStringArray(R.array.lines);
        int posLines = java.util.Arrays.asList(valuesMaxLines).indexOf(String.valueOf(maxLines));
        binding.spinnerMaxLine.setAdapter(new ArrayAdapter<>(getActivity(), R.layout.spinner_item_number, valuesMaxLines));
        binding.spinnerMaxLine.setSelection(posLines, true);
        binding.spinnerMaxLine.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id) {
                maxLines = Integer.parseInt(valuesMaxLines[pos]);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        final String[] valuesMaxColumn = getResources().getStringArray(R.array.columns);
        int posColumn = java.util.Arrays.asList(valuesMaxColumn).indexOf(String.valueOf(maxColumns));
        binding.spinnerMaxColumn.setAdapter(new ArrayAdapter<>(getActivity(), R.layout.spinner_item_number, valuesMaxColumn));
        binding.spinnerMaxColumn.setSelection(posColumn, true);
        binding.spinnerMaxColumn.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id) {
                maxColumns = Integer.parseInt(valuesMaxColumn[pos]);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        final String[] valuesRoom = getResources().getStringArray(R.array.rooms);
        binding.spinnerRoom.setAdapter(new ArrayAdapter<>(getActivity(), R.layout.spinner_item_number, valuesRoom));
        binding.spinnerRoom.setSelection(typeRoom, true);
        binding.spinnerRoom.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id) {
                typeRoom = pos;
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        binding.btnSaveDeviceInfo.setOnClickListener(view1 -> {
            if (binding.edtAndroidBoxID.getText().toString().isEmpty()){
                Toast.makeText(getActivity(), getString(R.string.miss_data_android_box), Toast.LENGTH_LONG).show();
            }
            else {
                try {

                    int androidBoxID = Integer.parseInt(binding.edtAndroidBoxID.getText().toString());
                    String roomName = binding.edtRoom.getText().toString();
                    String areaName = binding.edtArea.getText().toString();

                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putInt(MainActivity.KEY_LINE_NUMBER, maxLines);
                    editor.putInt(MainActivity.KEY_COLUMN_NUMBER, maxColumns);
                    editor.putInt(MainActivity.KEY_DEVICE_ID, androidBoxID);
                    editor.putString(MainActivity.KEY_ROOM_NAME, roomName);
                    editor.putString(MainActivity.KEY_PLACE_NAME, areaName);
                    editor.putInt(MainActivity.KEY_ROOM_TYPE, typeRoom);
                    editor.putBoolean(MainActivity.KEY_FIRST_TIME_OPEN, false);
                    editor.apply();

                    Toast.makeText(getActivity(), getString(R.string.saved_config), Toast.LENGTH_LONG).show();

                    binding.btnCloseConfig.callOnClick();

                } catch (Exception ex) {
                    Toast.makeText(getActivity(), "Error when parsing data", Toast.LENGTH_LONG).show();
                }


            }
        });

        binding.btnRefreshDevices.setOnClickListener(view1 ->  refresh());

        binding.btnReadMode.setOnClickListener(view1 -> {
            final String[] values = getResources().getStringArray(R.array.read_modes);
            int pos = withIoManager ? 0 : 1; // read_modes[0]=event/io-manager, read_modes[1]=direct
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle("Read mode");
            builder.setSingleChoiceItems(values, pos, (dialog, which) -> {
                withIoManager = (which == 0);
                dialog.dismiss();
            });
            builder.create().show();
        });


        binding.btnCloseConfig.setOnClickListener(view -> {
            Bundle args = new Bundle();
            args.putInt("device", prefs.getInt(MainActivity.USB_DEVICE, 0));
            args.putInt("port", prefs.getInt(MainActivity.USB_PORT, 0));
            args.putInt("baud", prefs.getInt(MainActivity.KEY_BAUD_RATE, 0));
            args.putBoolean("withIoManager", withIoManager);
            Fragment fragment = new TerminalFragment();
            fragment.setArguments(args);
            getFragmentManager().beginTransaction().replace(R.id.fragment, fragment, "terminal").commit();
        });

        return binding.getRoot();
    }



    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }


    void refresh() {
        UsbManager usbManager = (UsbManager) getActivity().getSystemService(Context.USB_SERVICE);
        UsbSerialProber usbDefaultProber = UsbSerialProber.getDefaultProber();
        UsbSerialProber usbCustomProber = CustomProber.getCustomProber();
        listItems = new ArrayList<>();
        for(UsbDevice device : usbManager.getDeviceList().values()) {
            UsbSerialDriver driver = usbDefaultProber.probeDevice(device);
            if(driver == null) {
                driver = usbCustomProber.probeDevice(device);
            }
            if(driver != null) {
                for(int port = 0; port < driver.getPorts().size(); port++)
                    listItems.add(new DeviceItem(device, port, driver));
            } else {
               // listItems.add(new DeviceItem(device, 0, null));
            }
        }


        ArrayAdapter<DeviceItem> adapter = new ArrayAdapter<>(getActivity(), R.layout.spinner_item, listItems);
        adapter.setDropDownViewResource(R.layout.spinner_item);
        binding.spinnerUSBDevices.setAdapter(adapter);
        binding.spinnerUSBDevices.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id) {
                onSelectDevice(listItems.get(pos));
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });
    }



    @Override
    public void onSelectDevice(DeviceItem item) {
        if(item.getDriver() == null) {
            Toast.makeText(getActivity(), "no driver", Toast.LENGTH_SHORT).show();
        } else {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt(MainActivity.USB_DEVICE, item.getDevice().getDeviceId());
            editor.putInt(MainActivity.USB_PORT, item.getPort());
            editor.putInt(MainActivity.USB_BAUD_RATE, baudRate);
            editor.putBoolean(MainActivity.USB_IO_MANAGER, withIoManager);
            editor.apply();

            Toast.makeText(getActivity(), getString(R.string.selected_devices), Toast.LENGTH_SHORT).show();
        }
    }


}
