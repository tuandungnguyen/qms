package com.ntd.qms;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
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
import com.ntd.qms.util.HexDump;

import java.util.ArrayList;

import static android.content.Context.MODE_PRIVATE;

public class ConfigFragment extends Fragment implements DeviceAdapter.ClickListener{

    private FragmentConfigBinding binding;

    private ArrayList<DeviceItem> listItems = new ArrayList<>();
    private DeviceAdapter deviceAdapter;
    private int baudRate = 38400;
    private boolean withIoManager = true;

    SharedPreferences prefs;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = DataBindingUtil.inflate(
                inflater, R.layout.fragment_config, container, false);

        prefs = getActivity().getSharedPreferences(MainActivity.MY_PREFS_NAME, MODE_PRIVATE);

        binding.edtMaxLine.setText("" + prefs.getInt(MainActivity.KEY_LINE_NUMBER, 1));
        binding.edtAndroidBoxID.setText("" + prefs.getInt(MainActivity.KEY_DEVICE_ID, 1));
        binding.edtHouseName.setText(prefs.getString(MainActivity.KEY_ROOM_NAME, ""));

        baudRate = prefs.getInt(MainActivity.KEY_BAUD_RATE, 38400);

        deviceAdapter = new DeviceAdapter(getActivity(), this);
        GridLayoutManager layoutManager = new GridLayoutManager(getActivity(), 1, RecyclerView.VERTICAL, false);
        binding.rcvDevices.setLayoutManager(layoutManager);
        binding.rcvDevices.setAdapter(deviceAdapter);


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

        final String[] valuesTableView = getResources().getStringArray(R.array.table_type);
        binding.spinnerTableViewMode.setAdapter(new ArrayAdapter<>(getActivity(), R.layout.spinner_item, valuesTableView));
        binding.spinnerTableViewMode.setSelection(posBR, true);
        binding.spinnerTableViewMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id) {
                /*baudRate = Integer.parseInt(valuesBR[pos]);
                SharedPreferences.Editor editor = prefs.edit();
                editor.putInt(MainActivity.KEY_BAUD_RATE, baudRate);
                editor.apply();*/
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        binding.btnSaveDeviceInfo.setOnClickListener(view1 -> {
            if (binding.edtAndroidBoxID.getText().toString().isEmpty() || binding.edtHouseName.getText().toString().isEmpty()){
                Toast.makeText(getActivity(), "Blank field. Can not save", Toast.LENGTH_LONG).show();
            } else {
                try {
                    int maxLine = Integer.parseInt(binding.edtMaxLine.getText().toString());
                    int androidBoxID = Integer.parseInt(binding.edtAndroidBoxID.getText().toString());
                    String roomName = binding.edtHouseName.getText().toString();

                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putInt(MainActivity.KEY_LINE_NUMBER, maxLine);
                    editor.putInt(MainActivity.KEY_DEVICE_ID, androidBoxID);
                    editor.putString(MainActivity.KEY_ROOM_NAME, roomName);
                    editor.apply();

                    Toast.makeText(getActivity(), "Data is saved", Toast.LENGTH_LONG).show();

                } catch (Exception ex) {
                    Toast.makeText(getActivity(), "Error when parsing data", Toast.LENGTH_LONG).show();
                }
            }
        });

        binding.btnRefreshDevices.setOnClickListener(view1 ->  refresh());

        /*
        binding.btnBaudRate.setOnClickListener(view1 -> {
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

         */

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
            args.putInt("device", 0);
            args.putInt("port", 0);
            args.putInt("baud", baudRate);
            args.putBoolean("withIoManager", withIoManager);
            Fragment fragment = new TerminalFragment();
            fragment.setArguments(args);
            getFragmentManager().beginTransaction().replace(R.id.fragment, fragment, "terminal").addToBackStack(null).commit();
        });


        return binding.getRoot();
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
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt(MainActivity.USB_DEVICE, item.getDevice().getDeviceId());
            editor.putInt(MainActivity.USB_PORT, item.getPort());
            editor.putInt(MainActivity.USB_BAUD_RATE, baudRate);
            editor.putBoolean(MainActivity.USB_IO_MANAGER, withIoManager);
            editor.apply();

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
