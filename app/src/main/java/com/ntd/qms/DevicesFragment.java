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
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.ntd.qms.adapter.DeviceAdapter;
import com.ntd.qms.data.DeviceItem;

import java.util.ArrayList;

import static android.content.Context.MODE_PRIVATE;

public class DevicesFragment extends Fragment implements DeviceAdapter.ClickListener{

    private ArrayList<DeviceItem> listItems = new ArrayList<>();
    private DeviceAdapter deviceAdapter;
    private int baudRate = 38400;
    private boolean withIoManager = true;

    Button btnRefreshDevices, btnBaudRate, btnReadMode, btnSave;
    EditText edtDeviceID, edtRoomName;
    RecyclerView rcvDevices;

    SharedPreferences prefs;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_config, container, false);

        btnRefreshDevices = view.findViewById(R.id.btnRefreshDevices);
        btnBaudRate = view.findViewById(R.id.btnBaudRate);
        btnReadMode = view.findViewById(R.id.btnReadMode);
        btnSave = view.findViewById(R.id.btnSaveDeviceInfo);

        rcvDevices = view.findViewById(R.id.rcvDevices);

        edtDeviceID = view.findViewById(R.id.edtAndroidBoxID);
        edtRoomName = view.findViewById(R.id.edtHouseName);



        prefs = getActivity().getSharedPreferences(MainActivity.MY_PREFS_NAME, MODE_PRIVATE);
        edtDeviceID.setText("" + prefs.getInt(MainActivity.KEY_DEVICE_ID, 1));
        edtRoomName.setText(prefs.getString(MainActivity.KEY_ROOM_NAME, ""));

        deviceAdapter = new DeviceAdapter(getActivity(), this);
        GridLayoutManager layoutManager = new GridLayoutManager(getActivity(), 1, RecyclerView.VERTICAL, false);
        rcvDevices.setLayoutManager(layoutManager);
        rcvDevices.setAdapter(deviceAdapter);

        btnSave.setOnClickListener(view1 -> {
            if (edtDeviceID.getText().toString().isEmpty() || edtRoomName.getText().toString().isEmpty()){
                Toast.makeText(getActivity(), "Blank field. Can not save", Toast.LENGTH_LONG).show();
            } else {
                try {
                    int androidBoxID = Integer.parseInt(edtDeviceID.getText().toString());
                    String roomName = edtRoomName.getText().toString();

                    SharedPreferences.Editor editor = getActivity().getSharedPreferences(MainActivity.MY_PREFS_NAME, MODE_PRIVATE).edit();
                    editor.putInt(MainActivity.KEY_DEVICE_ID, androidBoxID);
                    editor.putString(MainActivity.KEY_ROOM_NAME, roomName);
                    editor.apply();

                    Toast.makeText(getActivity(), "Data is saved", Toast.LENGTH_LONG).show();

                } catch (Exception ex) {
                    Toast.makeText(getActivity(), "Error when parsing data", Toast.LENGTH_LONG).show();
                }

            }
        });

        btnRefreshDevices.setOnClickListener(view1 ->  refresh());

        btnBaudRate.setOnClickListener(view1 -> {
            final String[] values = getResources().getStringArray(R.array.baud_rates);
            int pos = java.util.Arrays.asList(values).indexOf(String.valueOf(baudRate));
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle("Baud rate");
            builder.setSingleChoiceItems(values, pos, (dialog, which) -> {
                baudRate = Integer.parseInt(values[which]);
                dialog.dismiss();
            });
            builder.create().show();
        });

        btnReadMode.setOnClickListener(view1 -> {
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


        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        /*setListAdapter(null);
        View header = getActivity().getLayoutInflater().inflate(R.layout.device_list_header, null, false);
        getListView().addHeaderView(header, null, false);
        setEmptyText("<no USB devices found>");
        ((TextView) getListView().getEmptyView()).setTextSize(18);
        setListAdapter(listAdapter);*/
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
                listItems.add(new DeviceItem(device, 0, null));
            }
        }
        //deviceAdapter.notifyDataSetChanged();
        deviceAdapter.getDiffer().submitList(listItems);
    }



    @Override
    public void onSelectDevice(DeviceItem item) {
        if(item.getDriver() == null) {
            Toast.makeText(getActivity(), "no driver", Toast.LENGTH_SHORT).show();
        } else {
            Bundle args = new Bundle();
            args.putInt("device", item.getDevice().getDeviceId());
            args.putInt("port", item.getPort());
            args.putInt("baud", baudRate);
            args.putBoolean("withIoManager", withIoManager);
            Fragment fragment = new TerminalFragment();
            fragment.setArguments(args);
            getFragmentManager().beginTransaction().replace(R.id.fragment, fragment, "terminal").addToBackStack(null).commit();
        }
    }


}
