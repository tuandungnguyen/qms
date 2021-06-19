package com.ntd.qms;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
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
import android.view.animation.AnimationUtils;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.encoders.annotations.Encodable;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;
import com.ntd.qms.adapter.OrderAndRoomAdapter;
import com.ntd.qms.data.OrderAndRoomItem;
import com.ntd.qms.databinding.FragmentTerminalBinding;
import com.ntd.qms.util.HexDump;
import com.ntd.qms.util.Utils;

import java.io.ByteArrayOutputStream;
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

    private int deviceId, portNum, baudRate, deviceVendorId;
    private String deviceProductName;
    private boolean withIoManager;

    private int androidBoxID;
    private String roomName, areaName;

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

    boolean getdata = false;
    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    private Handler handlerRemover;
    private Runnable removeRunner = new Runnable() {
        @Override
        public void run() {
            int maxItem = 3;
            try {
                maxItem = prefs.getInt(MainActivity.KEY_COLUMN_NUMBER, 1) * prefs.getInt(MainActivity.KEY_LINE_NUMBER, 1);
            } catch (Exception ignored) {
            }

            if (listItem.size() > maxItem){
                listItem.remove(0);
                ArrayList<OrderAndRoomItem> newListItem = new ArrayList<>(listItem);
                orderAndRoomAdapter.getDiffer().submitList(newListItem);
            }
        }
    };

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
    }

    /*
     * Lifecycle
     */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);

        prefs = getActivity().getSharedPreferences(MainActivity.MY_PREFS_NAME, MODE_PRIVATE);

        deviceId = prefs.getInt(MainActivity.USB_DEVICE, 0);
        deviceVendorId = prefs.getInt(MainActivity.USB_DEVICE_VENDOR, 0);
        deviceProductName = prefs.getString(MainActivity.USB_DEVICE_PRODUCE_NAME, "");
        portNum = prefs.getInt(MainActivity.USB_PORT, 0);
        baudRate = prefs.getInt(MainActivity.USB_BAUD_RATE, 0);
        withIoManager = prefs.getBoolean(MainActivity.USB_IO_MANAGER, false);
        handlerRemover = new Handler(Looper.getMainLooper());
    }

    @Override
    public void onResume() {
        super.onResume();

        getActivity().registerReceiver(broadcastReceiver, new IntentFilter(INTENT_ACTION_GRANT_USB));

        mainLooper = new Handler(Looper.getMainLooper());

        if (usbPermission == UsbPermission.Unknown || usbPermission == UsbPermission.Granted) {
            mainLooper.post(this::connect);
        }

    }

    @Override
    public void onPause() {
        if (connected) {
            status("disconnected");
            status("disconnected");
            disconnect();
        }
        getActivity().unregisterReceiver(broadcastReceiver);
        super.onPause();
    }

    @Override
    public void onStop() {
        super.onStop();
        handlerRemover.removeCallbacks(removeRunner);
    }

    /*
     * UI
     */
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = DataBindingUtil.inflate(
                inflater, R.layout.fragment_terminal, container, false);

        int maxColumn = prefs.getInt(MainActivity.KEY_COLUMN_NUMBER, 1);
        orderAndRoomAdapter = new OrderAndRoomAdapter(getActivity(), maxColumn);
        GridLayoutManager layoutManager = new GridLayoutManager(getActivity(), maxColumn, RecyclerView.VERTICAL, false);
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

        if (prefs.getInt(MainActivity.KEY_LINE_NUMBER, 1) > 1) {
            binding.layoutCounterDisplay.setVisibility(View.GONE);
            binding.layoutMainDisplay.setVisibility(View.VISIBLE);
            binding.tvPlace2.setSelected(true);
        } else {
            binding.layoutCounterDisplay.setVisibility(View.VISIBLE);
            binding.layoutMainDisplay.setVisibility(View.GONE);
            binding.tvPlace1.setSelected(true);
            binding.tvStatus.startScroll();
        }

        androidBoxID = prefs.getInt(MainActivity.KEY_DEVICE_ID, 1);
        roomName = prefs.getString(MainActivity.KEY_ROOM_NAME, "");
        areaName = prefs.getString(MainActivity.KEY_PLACE_NAME, "");

        if (roomName != null & !roomName.isEmpty()) {
            binding.tvRoom.setText(roomName);
        } else {
            String[] valuesRoom = getResources().getStringArray(R.array.rooms);
            String prefix = java.util.Arrays.asList(valuesRoom).get(prefs.getInt(MainActivity.KEY_ROOM_TYPE, 0));
            binding.tvRoom.setText(prefix + " " + androidBoxID);
        }

        binding.tvPlace1.setText(areaName);
        binding.tvPlace2.setText(areaName);

        binding.btnMenuConfig.setOnClickListener(view -> {
            if (getFragmentManager() != null) {
                getFragmentManager().beginTransaction().replace(R.id.fragment, new ConfigFragment(), "devices").commit();
            }
        });

        binding.btnSendTestData.setOnClickListener(view -> {
            sendTestData();
        });


        if (prefs.getInt(MainActivity.KEY_COLUMN_NUMBER, 1) == 1) {
            binding.tvCallingNumber2.setVisibility(View.GONE);
            binding.tvCallingRoom2.setVisibility(View.GONE);
        }

        try {
            String[] valuesRoom = getResources().getStringArray(R.array.rooms);
            String prefix = java.util.Arrays.asList(valuesRoom).get(prefs.getInt(MainActivity.KEY_ROOM_TYPE, 0));
            binding.tvCallingRoom1.setText(prefix);
            binding.tvCallingRoom2.setText(prefix);
        } catch (Exception ignored) {

        }

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
            int len = data.length;
            //byte[] buffer = null;
            for (int i = 0; i < len; i++) {
                if (data[i] == 0x02) {
                    baos = new ByteArrayOutputStream();
                    getdata = true;
                }
                if (getdata) {
                    if (data[i] > 0x03) {
                        baos.write(data[i]);
                    } else if (data[i] == 0x03) {
                        getdata = false;
                        //Dua chuoi di xu ly
                        receive(baos.toByteArray());
                    }
                }
            }

            // receive(data);
            //  Toast.makeText(getActivity(), "getData " + HexDump.bytesToString(data), Toast.LENGTH_SHORT).show();

        });
    }

    @Override
    public void onRunError(Exception e) {
        mainLooper.post(() -> {
            status("_connection lost: " + e.getMessage());
            pauseBanner();
            disconnect();
        });
    }


    /*
     * Serial + UI
     */
    private void connect() {
        UsbDevice device = null;
        UsbManager usbManager = (UsbManager) getActivity().getSystemService(Context.USB_SERVICE);

        /*for (UsbDevice v : usbManager.getDeviceList().values()) {
            if (v.getDeviceId() == deviceId)
                device = v;
        }*/

        for (UsbDevice v : usbManager.getDeviceList().values()) {
            if (v.getVendorId() == deviceVendorId && v.getProductName().equals(deviceProductName))
                device = v;
        }


        if (device == null) {
            status("connection failed: device not found");
            Toast.makeText(getActivity(), "Lỗi kết nối thiết bị ", Toast.LENGTH_SHORT).show();
            // binding.btnMenuConfig.callOnClick();
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
            resumeBanner();
            connected = true;
            controlLines.start();
        } catch (Exception e) {
            status("Connection failed: " + e.getMessage());
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
        if (!connected) {
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
        }
    }

    private void read() {
        if (!connected) {
            Toast.makeText(getActivity(), "not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            byte[] buffer = new byte[8192];
            int len = usbSerialPort.read(buffer, READ_WAIT_MILLIS);
            Toast.makeText(getActivity(), "buffer_len = " + len, Toast.LENGTH_SHORT).show();
            receive(Arrays.copyOf(buffer, len));

        } catch (IOException e) {
            // when using read with timeout, USB bulkTransfer returns -1 on timeout _and_ errors
            // like connection loss, so there is typically no exception thrown here on error
            status("Connection lost: " + e.getMessage());
            disconnect();
        }
    }


    private void sendTestData() {
        String dataString = binding.edtTestData.getText().toString();
        byte[] bs = dataString.getBytes();
        receive(bs);
    }

    private void pauseBanner() {
        binding.bannerCounterDisplay.setBackgroundColor(getActivity().getColor(R.color.grey));
        binding.footerCounterDisplay.setBackgroundColor(getActivity().getColor(R.color.grey));
        binding.bannerMainDisplay.setBackgroundColor(getActivity().getColor(R.color.grey));
    }

    public void resumeBanner() {
        binding.bannerCounterDisplay.setBackgroundColor(getActivity().getColor(R.color.blue));
        binding.footerCounterDisplay.setBackgroundColor(getActivity().getColor(R.color.blue));
        binding.bannerMainDisplay.setBackgroundColor(getActivity().getColor(R.color.blue));
    }

    private void receive(byte[] data) {
        String receiveString = HexDump.bytesToString(data);

        binding.tvTextReceive.setText("Receive Text: " + receiveString);

       /* SpannableStringBuilder spn = new SpannableStringBuilder();
        spn.append("receive " + data.length + " bytes\n");
        if (data.length > 0)
            spn.append(HexDump.dumpHexString(data) + "\n");

        binding.receiveText.append(spn);*/

        try {

            if (receiveString.length() > 0 && receiveString.contains(",")) {

                String[] receiveStrings = receiveString.split(",");

                //Hiden keys
                if (receiveStrings[0].equals("0") && receiveStrings[1].equals("-99") & receiveStrings[2].equals("99")) {
                    binding.layoutCounterDisplay.setVisibility(View.GONE);
                    binding.layoutMainDisplay.setVisibility(View.GONE);
                    binding.layoutControlPanel.setVisibility(View.VISIBLE);
                }

                //Hien Thi
                if (receiveStrings[0].equals("" + androidBoxID) || receiveStrings[0].equals("0") && receiveStrings[2].equals("103")) {

                    if (prefs.getInt(MainActivity.KEY_LINE_NUMBER, 1) == 1) {

                        //Counter Display
                        binding.layoutCounterDisplay.setVisibility(View.VISIBLE);
                        binding.layoutMainDisplay.setVisibility(View.GONE);
                        binding.tvPlace1.setSelected(true);

                        //Check android box with second param.
                        if (receiveStrings[1].equals("" + androidBoxID)) {
                            int param1 = Integer.parseInt(receiveStrings[3]);
                            int bitmaskA = 0x3FFF;

                            if (param1 > 0) {
                                binding.tvStatus.setVisibility(View.GONE);
                                binding.tvNumber.setVisibility(View.VISIBLE);
                                binding.tvNumber.setAnimation(AnimationUtils.loadAnimation(getActivity(), R.anim.slide_down));
                                binding.tvNumber.setText(Utils.formatQueueNumber((param1 & bitmaskA), 4));
                            } else if (param1 == -3) {
                                binding.tvNumber.setVisibility(View.GONE);
                                binding.tvStatus.setVisibility(View.VISIBLE);
                                binding.tvStatus.setText(getText(R.string.status_pause));
                            } else if (param1 == -6) {
                                binding.tvNumber.setVisibility(View.GONE);
                                binding.tvStatus.setVisibility(View.VISIBLE);
                                binding.tvStatus.setText(getText(R.string.status_welcome));
                            } else if (param1 == -7) {
                                binding.tvNumber.setVisibility(View.GONE);
                                binding.tvStatus.setVisibility(View.VISIBLE);
                                binding.tvStatus.setText(getText(R.string.status_thankyou));
                            }


                        }

                    } else if (prefs.getInt(MainActivity.KEY_LINE_NUMBER, 1) > 1) {

                        //Main Display
                        binding.layoutMainDisplay.setVisibility(View.VISIBLE);
                        binding.layoutCounterDisplay.setVisibility(View.GONE);
                        binding.tvPlace2.setSelected(true);

                        try {
                            int param1 = Integer.parseInt(receiveStrings[3]);

                            if (param1 <= 0)
                                return;

                            int param2 = Integer.parseInt(receiveStrings[4]);
                            int bitmaskA = 0x3FFF;
                            int bitmaskB = 0x007F;

                            int queueNumber = param1 & bitmaskA;
                            int room = param2 & bitmaskB;
                            int direction = (param2 >> 14) & 0x03;

                            OrderAndRoomItem item = new OrderAndRoomItem(queueNumber, direction, room);
                            int tmpRoom = room;
                            int tmpQueueNumber = queueNumber;
                            listItem.removeIf(s -> s.getRoom() == tmpRoom);
                            listItem.removeIf(s -> s.getQueueNumber() == tmpQueueNumber);
                            listItem.add(item);

                            ArrayList<OrderAndRoomItem> newListItem = new ArrayList<>();
                            newListItem.addAll(listItem);
                            orderAndRoomAdapter.getDiffer().submitList(newListItem);
                            new Handler(Looper.getMainLooper()).postDelayed(() -> binding.rcvOrders.smoothScrollToPosition(listItem.size() - 1),500);
                            handlerRemover.postDelayed(removeRunner, 500);

                        } catch (Exception ex) {
                            Toast.makeText(getActivity(), ex.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            }

        } catch (Exception ex) {
            Toast.makeText(getActivity(), "Error: " + ex.getMessage(), Toast.LENGTH_LONG).show();
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
