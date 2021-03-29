package com.ntd.qms;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;
import com.ntd.qms.adapter.OrderAndRoomAdapter;
import com.ntd.qms.data.OrderAndRoomItem;
import com.ntd.qms.databinding.FragmentTerminalBinding;
import com.ntd.qms.util.Utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.concurrent.Executors;

import static android.content.Context.MODE_PRIVATE;

public class TerminalFragment extends Fragment implements SerialInputOutputManager.Listener {

    private FragmentTerminalBinding binding;

    private enum UsbPermission {Unknown, Requested, Granted, Denied}


    private static final String INTENT_ACTION_GRANT_USB = BuildConfig.APPLICATION_ID + ".GRANT_USB";
    private static final int WRITE_WAIT_MILLIS = 2000;
    private static final int READ_WAIT_MILLIS = 2000;

    private int deviceId, portNum, baudRate;
    private boolean withIoManager;

    private int androidBoxID;
    private String roomName;

    private BroadcastReceiver broadcastReceiver;
    private Handler mainLooper;

    private OrderAndRoomAdapter orderAndRoomAdapter;

    ArrayList<OrderAndRoomItem> listItem;

    private ControlLines controlLines;

    private SerialInputOutputManager usbIoManager;
    private UsbSerialPort usbSerialPort;
    private UsbPermission usbPermission = UsbPermission.Unknown;
    private boolean connected = false;

    SharedPreferences prefs;

    public TerminalFragment() {
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(INTENT_ACTION_GRANT_USB)) {
                    usbPermission = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                            ? UsbPermission.Granted : UsbPermission.Denied;
                    connect();
                }
            }
        };
        mainLooper = new Handler(Looper.getMainLooper());
    }

    /*
     * Lifecycle
     */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRetainInstance(true);
        deviceId = getArguments().getInt("device");
        portNum = getArguments().getInt("port");
        baudRate = getArguments().getInt("baud");
        withIoManager = getArguments().getBoolean("withIoManager");
    }

    @Override
    public void onResume() {
        super.onResume();
        getActivity().registerReceiver(broadcastReceiver, new IntentFilter(INTENT_ACTION_GRANT_USB));

        if (usbPermission == UsbPermission.Unknown || usbPermission == UsbPermission.Granted)
            mainLooper.post(this::connect);
    }

    @Override
    public void onPause() {
        if (connected) {
            status("disconnected");
            disconnect();
        }
        getActivity().unregisterReceiver(broadcastReceiver);
        super.onPause();
    }

    /*
     * UI
     */
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        binding = DataBindingUtil.inflate(
                inflater, R.layout.fragment_terminal, container, false);



        orderAndRoomAdapter = new OrderAndRoomAdapter(getActivity());
        GridLayoutManager layoutManager = new GridLayoutManager(getActivity(), 1, RecyclerView.VERTICAL, false);
        binding.rcvOrders.setLayoutManager(layoutManager);
        binding.rcvOrders.setAdapter(orderAndRoomAdapter);

        listItem = new ArrayList<>();

        binding.receiveText.setTextColor(getResources().getColor(R.color.colorRecieveText)); // set as default color to reduce number of spans
        binding.receiveText.setMovementMethod(ScrollingMovementMethod.getInstance());

        binding.sendBtn.setOnClickListener(v -> send(binding.sendText.getText().toString()));

        controlLines = new ControlLines(binding.getRoot());
        if (withIoManager) {
            binding.receiveBtn.setVisibility(View.GONE);
        } else {
            binding.receiveBtn.setOnClickListener(v -> read());
        }


        prefs = getActivity().getSharedPreferences(MainActivity.MY_PREFS_NAME, MODE_PRIVATE);
        androidBoxID = prefs.getInt(MainActivity.KEY_DEVICE_ID, 1);
        roomName = prefs.getString(MainActivity.KEY_ROOM_NAME, "");

        binding.tvDeviceId.setText(getActivity().getString(R.string.room) + " " + androidBoxID);
        binding.tvRoomName.setText(roomName);
        binding.tvRoomName2.setText(roomName);

        return binding.getRoot();
    }


    /*  @Override
      public boolean onOptionsItemSelected(MenuItem item) {
          int id = item.getItemId();
          if (id == R.id.clear) {
              receiveText.setText("");
              return true;
          } else if( id == R.id.send_break) {
              if(!connected) {
                  Toast.makeText(getActivity(), "not connected", Toast.LENGTH_SHORT).show();
              } else {
                  try {
                      usbSerialPort.setBreak(true);
                      Thread.sleep(100); // should show progress bar instead of blocking UI thread
                      usbSerialPort.setBreak(false);
                      SpannableStringBuilder spn = new SpannableStringBuilder();
                      spn.append("send <break>\n");
                      spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorSendText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                      receiveText.append(spn);
                  } catch(UnsupportedOperationException ignored) {
                      Toast.makeText(getActivity(), "BREAK not supported", Toast.LENGTH_SHORT).show();
                  } catch(Exception e) {
                      Toast.makeText(getActivity(), "BREAK failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                  }
              }
              return true;
          } else {
              return super.onOptionsItemSelected(item);
          }
      }
  */
    /*
     * Serial
     */
    @Override
    public void onNewData(byte[] data) {
        mainLooper.post(() -> {
            receive(data);
        });
    }

    @Override
    public void onRunError(Exception e) {
        mainLooper.post(() -> {
            status("connection lost: " + e.getMessage());
            disconnect();
        });
    }

    /*
     * Serial + UI
     */
    private void connect() {
        UsbDevice device = null;
        UsbManager usbManager = (UsbManager) getActivity().getSystemService(Context.USB_SERVICE);
        for (UsbDevice v : usbManager.getDeviceList().values())
            if (v.getDeviceId() == deviceId)
                device = v;
        if (device == null) {
            status("connection failed: device not found");
            return;
        }
        UsbSerialDriver driver = UsbSerialProber.getDefaultProber().probeDevice(device);
        if (driver == null) {
            driver = CustomProber.getCustomProber().probeDevice(device);
        }
        if (driver == null) {
            status("connection failed: no driver for device");
            return;
        }
        if (driver.getPorts().size() < portNum) {
            status("connection failed: not enough ports at device");
            return;
        }
        usbSerialPort = driver.getPorts().get(portNum);
        UsbDeviceConnection usbConnection = usbManager.openDevice(driver.getDevice());
        if (usbConnection == null && usbPermission == UsbPermission.Unknown && !usbManager.hasPermission(driver.getDevice())) {
            usbPermission = UsbPermission.Requested;
            PendingIntent usbPermissionIntent = PendingIntent.getBroadcast(getActivity(), 0, new Intent(INTENT_ACTION_GRANT_USB), 0);
            usbManager.requestPermission(driver.getDevice(), usbPermissionIntent);
            return;
        }
        if (usbConnection == null) {
            if (!usbManager.hasPermission(driver.getDevice()))
                status("connection failed: permission denied");
            else
                status("connection failed: open failed");
            return;
        }

        try {
            usbSerialPort.open(usbConnection);
            usbSerialPort.setParameters(baudRate, 8, 1, UsbSerialPort.PARITY_NONE);
            if (withIoManager) {
                usbIoManager = new SerialInputOutputManager(usbSerialPort, this);
                Executors.newSingleThreadExecutor().submit(usbIoManager);
            }
            status("connected");
            connected = true;
            controlLines.start();
        } catch (Exception e) {
            status("connection failed: " + e.getMessage());
            disconnect();
        }
    }

    private void disconnect() {
        connected = false;
        controlLines.stop();
        if (usbIoManager != null)
            usbIoManager.stop();
        usbIoManager = null;
        try {
            usbSerialPort.close();
        } catch (IOException ignored) {
        }
        usbSerialPort = null;
    }

    private void send(String str) {

        receive(null);

       /* if (!connected) {
            Toast.makeText(getActivity(), "not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            byte[] data = (str + '\n').getBytes();
            SpannableStringBuilder spn = new SpannableStringBuilder();
            spn.append("send " + data.length + " bytes\n");
            spn.append(HexDump.dumpHexString(data) + "\n");
            spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorSendText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            binding.receiveText.append(spn);
            usbSerialPort.write(data, WRITE_WAIT_MILLIS);
        } catch (Exception e) {
            onRunError(e);
        }*/
    }

    private void read() {
        if (!connected) {
            Toast.makeText(getActivity(), "not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            byte[] buffer = new byte[1];
            int len = usbSerialPort.read(buffer, READ_WAIT_MILLIS);
            receive(Arrays.copyOf(buffer, len));


        } catch (IOException e) {
            // when using read with timeout, USB bulkTransfer returns -1 on timeout _and_ errors
            // like connection loss, so there is typically no exception thrown here on error
            status("connection lost: " + e.getMessage());
            disconnect();
        }
    }


    private void receive(byte[] data) {

       /* SpannableStringBuilder spn = new SpannableStringBuilder();
        spn.append("receive " + data.length + " bytes\n");
        if (data.length > 0)
            spn.append(HexDump.dumpHexString(data) + "\n");

        binding.receiveText.append(spn);

        String receiveString = HexDump.bytesToString(data);

        */

        String receiveString = "0,2,103,59,16385,";

        binding.tvTextReceive.setText("Receive Text: \n" + receiveString);
        Toast.makeText(getActivity(), receiveString, Toast.LENGTH_SHORT).show();

        if (receiveString.length() > 0 && receiveString.contains(",")) {

            String[] receiveStrings = receiveString.split(",");

            //Hien Thi
            if (receiveStrings[0].equals("0") && receiveStrings[2].equals("103")) {

                if (prefs.getInt(MainActivity.KEY_LINE_NUMBER, 1) == 1) {

                    //Counter Display
                    binding.layoutCounterDisplay.setVisibility(View.VISIBLE);
                    binding.layoutMainDisplay.setVisibility(View.GONE);

                    if (receiveStrings[1].equals("" + androidBoxID)){
                        binding.tvNumber.setText(Utils.formatQueueNumber(receiveStrings[3],4));
                        binding.tvRoomName.setSelected(true);
                    }

                } else if (prefs.getInt(MainActivity.KEY_LINE_NUMBER, 1) > 1) {

                    //Main Display
                    binding.layoutMainDisplay.setVisibility(View.VISIBLE);
                    binding.layoutCounterDisplay.setVisibility(View.GONE);

                    try {
                        String res = Integer.toBinaryString(Integer.parseInt(receiveStrings[4]));
                        while (res.length() < 16) {
                            res += '0';
                        }
                        int direction = Integer.parseInt(res.substring(0, 2), 2);
                        int room = Integer.parseInt(res.substring(9, 16), 2);

                        OrderAndRoomItem item = new OrderAndRoomItem(receiveStrings[3], direction, room);
                        listItem.add(item);
                        ArrayList<OrderAndRoomItem> newListItem = new ArrayList<>();
                        newListItem.addAll(listItem);
                        orderAndRoomAdapter.getDiffer().submitList(newListItem);

                    } catch (Exception ex) {
                        Toast.makeText(getActivity(), ex.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                    }


                }


            }
        }




    }

    public void status(String str) {
        SpannableStringBuilder spn = new SpannableStringBuilder(str + '\n');
        spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorStatusText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        binding.receiveText.append(spn);
    }

    class ControlLines {
        private static final int refreshInterval = 200; // msec

        private Runnable runnable;
        private ToggleButton rtsBtn, ctsBtn, dtrBtn, dsrBtn, cdBtn, riBtn;

        ControlLines(View view) {
            runnable = this::run; // w/o explicit Runnable, a new lambda would be created on each postDelayed, which would not be found again by removeCallbacks

            rtsBtn = view.findViewById(R.id.controlLineRts);
            ctsBtn = view.findViewById(R.id.controlLineCts);
            dtrBtn = view.findViewById(R.id.controlLineDtr);
            dsrBtn = view.findViewById(R.id.controlLineDsr);
            cdBtn = view.findViewById(R.id.controlLineCd);
            riBtn = view.findViewById(R.id.controlLineRi);
            rtsBtn.setOnClickListener(this::toggle);
            dtrBtn.setOnClickListener(this::toggle);
        }

        private void toggle(View v) {
            ToggleButton btn = (ToggleButton) v;
            if (!connected) {
                btn.setChecked(!btn.isChecked());
                Toast.makeText(getActivity(), "not connected", Toast.LENGTH_SHORT).show();
                return;
            }
            String ctrl = "";
            try {
                if (btn.equals(rtsBtn)) {
                    ctrl = "RTS";
                    usbSerialPort.setRTS(btn.isChecked());
                }
                if (btn.equals(dtrBtn)) {
                    ctrl = "DTR";
                    usbSerialPort.setDTR(btn.isChecked());
                }
            } catch (IOException e) {
                status("set" + ctrl + "() failed: " + e.getMessage());
            }
        }

        private void run() {
            if (!connected)
                return;
            try {
                EnumSet<UsbSerialPort.ControlLine> controlLines = usbSerialPort.getControlLines();
                rtsBtn.setChecked(controlLines.contains(UsbSerialPort.ControlLine.RTS));
                ctsBtn.setChecked(controlLines.contains(UsbSerialPort.ControlLine.CTS));
                dtrBtn.setChecked(controlLines.contains(UsbSerialPort.ControlLine.DTR));
                dsrBtn.setChecked(controlLines.contains(UsbSerialPort.ControlLine.DSR));
                cdBtn.setChecked(controlLines.contains(UsbSerialPort.ControlLine.CD));
                riBtn.setChecked(controlLines.contains(UsbSerialPort.ControlLine.RI));
                mainLooper.postDelayed(runnable, refreshInterval);
            } catch (IOException e) {
                status("getControlLines() failed: " + e.getMessage() + " -> stopped control line refresh");
            }
        }

        void start() {
            if (!connected)
                return;
            try {
                EnumSet<UsbSerialPort.ControlLine> controlLines = usbSerialPort.getSupportedControlLines();
                if (!controlLines.contains(UsbSerialPort.ControlLine.RTS))
                    rtsBtn.setVisibility(View.INVISIBLE);
                if (!controlLines.contains(UsbSerialPort.ControlLine.CTS))
                    ctsBtn.setVisibility(View.INVISIBLE);
                if (!controlLines.contains(UsbSerialPort.ControlLine.DTR))
                    dtrBtn.setVisibility(View.INVISIBLE);
                if (!controlLines.contains(UsbSerialPort.ControlLine.DSR))
                    dsrBtn.setVisibility(View.INVISIBLE);
                if (!controlLines.contains(UsbSerialPort.ControlLine.CD))
                    cdBtn.setVisibility(View.INVISIBLE);
                if (!controlLines.contains(UsbSerialPort.ControlLine.RI))
                    riBtn.setVisibility(View.INVISIBLE);
                run();
            } catch (IOException e) {
                Toast.makeText(getActivity(), "getSupportedControlLines() failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }

        void stop() {
            mainLooper.removeCallbacks(runnable);
            rtsBtn.setChecked(false);
            ctsBtn.setChecked(false);
            dtrBtn.setChecked(false);
            dsrBtn.setChecked(false);
            cdBtn.setChecked(false);
            riBtn.setChecked(false);
        }
    }
}
