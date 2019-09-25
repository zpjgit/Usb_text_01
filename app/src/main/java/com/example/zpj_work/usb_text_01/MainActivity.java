package com.example.zpj_work.usb_text_01;

import android.content.Context;
import android.hardware.usb.UsbConfiguration;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.media.midi.MidiDevice;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Switch;
import android.widget.Toast;

import java.io.IOException;

import static android.system.Os.close;


public class MainActivity extends AppCompatActivity {
    private UsbManager mUsbManager;                      //usb管理类
    String TAG = "hik";
    UsbEndpoint mReadEndpoint, mWriteEndpoint;
    UsbDeviceConnection mConnection = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mUsbManager= (UsbManager) getSystemService(USB_SERVICE);
        if (mUsbManager == null) {
            Toast.makeText(this, "手机不支持OTG", Toast.LENGTH_SHORT).show();
            return;
        }
    }






    /**
     * 连接设备
     */
    private void connectDevice(UsbDevice usbDevice){
        //Android标准的api，判断设备是否有连接权限
        if (mUsbManager.hasPermission(usbDevice)){
            //Android标准的API，打开设备，返回的是UsbDeviceConnection对象，也是Android标准API
            UsbDeviceConnection connection = mUsbManager.openDevice(usbDevice);
            try {
                open(connection);
                setParameters(ScaleDevice.BAUDRATE, ScaleDevice.DATABITS,
                        UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            } catch (IOException e) {
                e.printStackTrace();
                try {
                    mScaleDevicePort.close();
                } catch (IOException e2) {
                    e2.printStackTrace();
                }
            }
        }
    }
    //--------------------------------------------------------------------------------------------
    private void open(UsbDeviceConnection connection) throws IOException {

        UsbDeviceConnection mConnection = null;
        if (mConnection != null) {
            throw new IOException("Already opened");
        }
        mConnection = connection;
        try {
            //获取用于通信的interface
//            UsbDeviceConnection mDevice = null;
            UsbDevice mDevice = null;
            for (int i = 0; i<mDevice.getInterfaceCount(); i++) {
                //获取每一个interface
                UsbInterface usbIface = mDevice.getInterface(i);
                //声明当前interface, 也就是占用当前的 interface, true 表示 force, 是否强制占用, 返回boolean值, 表示是否占用成功.
                if (mConnection.claimInterface(usbIface, true)) {
                    Log.d(TAG, "claimInterface " + i + " SUCCESS");
                } else {
                    Log.d(TAG, "claimInterface " + i + " FAIL");
                }
            }
            //获取最后一个UsbInterface, 这可能是当前类型的 UsbDevice 的特性, 用最后一个 UsbInterface 进行通信.
            UsbInterface dataIface = mDevice.getInterface(mDevice.getInterfaceCount() - 1);
            //获取最后一个UsbInterface通信端点的数量, 并进行遍历
            for (int i=0; i<dataIface.describeContents(); i++) {
                //遍历UsbInterface包含所有的UsbEndpoint
                UsbEndpoint ep = dataIface.getEndpoint(i);
                //判断UsbEndpoint的类型, 也即是type. 这种类型的 UsbDevice 使用 USB_ENDPOINT_XFER_BULK这种类型的UsbEndpoint进行通信.
                if (ep.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    if (ep.getDirection() == UsbConstants.USB_DIR_IN) { //再判断UsbEndpoint的数据方向，很明显IN表示读入数据
                        mReadEndpoint = ep;  //获取了读端点
                    } else {
                        mWriteEndpoint = ep; //获取了写端点，这种类型是UsbConstants.USB_DIR_OUT
                    }
                }
            }

            //接下来是进行一些请求参数的配置，应该是根据不同的 UsbDevice 会进行不同的请求参数配置
            setConfigSingle(SILABSER_IFC_ENABLE_REQUEST_CODE, UART_ENABLE);
            setConfigSingle(SILABSER_SET_MHS_REQUEST_CODE, MCR_ALL | CONTROL_WRITE_DTR | CONTROL_WRITE_RTS);
            setConfigSingle(SILABSER_SET_BAUDDIV_REQUEST_CODE, BAUD_RATE_GEN_FREQ / DEFAULT_BAUD_RATE);

        } finally {
            boolean opened = false;
            if (!opened) {
                try {
                    close();
                } catch (IOException e) {

                }
            }
        }

    }
    //--------------------------------------------------------------------------------------------
    public void setParameters (int baudRate, int dataBits, int stopBits, int parity) throws IOException {
        //进行相应的波特率/停止位/数据位等配置参数的设置
        setBaudRate(baudRate);
        //数据位
        int configDataBits = 0;
        switch (dataBits) {
            case DATABITS_5:
                configDataBits |= 0x0500;
                break;
            case DATABITS_6:
                configDataBits |= 0x0600;
                break;
            case DATABITS_7:
                configDataBits |= 0x0700;
                break;
            case DATABITS_8:
                configDataBits |= 0x0800;
                break;
            default:
                configDataBits |= 0x0800;
                break;
        }

        switch (parity) {
            case PARITY_ODO:
                configDataBits |= 0x0010;
                break;
            case PARITY_EVEN:
                configDataBits |= 0x0020;
                break;
        }

        switch (stopBits) {
            case STOPBITS_1:
                configDataBits |= 0;
                break;
            case STOPBITS_2:
                configDataBits |= 2;
                break;
        }
        setConfigSingle(SILABSER_SET_LINE_CTL_REQUEST_CODE, configDataBits);
    }
    //--------------------------------------------------------------------------------------------
    //设置波特率
    private void setBaudRate(int baudRate) throws IOException {
            byte[] data = new byte[] {
                    (byte) (baudRate & 0xff),
                    (byte) ((baudRate >> 8) & 0xff),
                    (byte) ((baudRate >> 16) & 0xff),
                    (byte) ((baudRate >> 24) & 0xff)
            };
        int ret = mConnection.controlTransfer(REQTYPE_HOST_TO_DEVICE, SILABSER_SET_BAUDRATE, 0, 0, data, 4, USB_WRITE_TIMEOUT_MILLIS);
            if (ret < 0) {
                throw new IOException("Error setting baud rate.");
            }
    }
    //--------------------------------------------------------------------------------------------


}
