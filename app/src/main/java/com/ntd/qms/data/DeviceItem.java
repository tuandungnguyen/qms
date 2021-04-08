package com.ntd.qms.data;

import android.hardware.usb.UsbDevice;

import com.hoho.android.usbserial.driver.UsbSerialDriver;

public class DeviceItem {
    UsbDevice device;
    int port;
    UsbSerialDriver driver;

    public UsbDevice getDevice() {
        return device;
    }

    public void setDevice(UsbDevice device) {
        this.device = device;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public UsbSerialDriver getDriver() {
        return driver;
    }

    public void setDriver(UsbSerialDriver driver) {
        this.driver = driver;
    }

    public DeviceItem(UsbDevice device, int port, UsbSerialDriver driver) {
        this.device = device;
        this.port = port;
        this.driver = driver;
    }

    @Override
    public String toString() {

        if (getDriver() == null)
          return "<no driver>" + ", Product " + getDevice().getProductId();
        else
           return getDriver().getClass().getSimpleName().replace("SerialDriver","") + ", Product " + getDevice().getProductId();
    }
}
