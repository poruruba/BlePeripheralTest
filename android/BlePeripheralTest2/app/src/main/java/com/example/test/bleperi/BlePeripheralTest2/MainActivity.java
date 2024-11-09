package com.example.test.bleperi.BlePeripheralTest2;

import androidx.appcompat.app.AppCompatActivity;
import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Message;
import android.os.ParcelUuid;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

import java.util.UUID;

public class MainActivity extends AppCompatActivity implements UIHandler.Callback {
    static final int PERMISSION_REQUEST_CODE = 100;
    UIHandler handler;
    BluetoothManager mBleManager;
    BluetoothAdapter mBleAdapter;
    BluetoothLeAdvertiser mBtAdvertiser;
    BluetoothGattCharacteristic mPsdiCharacteristic;
    BluetoothGattCharacteristic mBtCharacteristic1;
    BluetoothGattCharacteristic mBtCharacteristic2;
    BluetoothGattCharacteristic mNotifyCharacteristic;
    BluetoothGattService btPsdiService;
    BluetoothGattService btGattService;
    BluetoothGattServer mBtGattServer;
    boolean mIsConnected = false;
    BluetoothDevice mConnectedDevice;

//    private static final short WIRELESS_BLE_MAX_L2CAP_SIZE = 23;

    private static final UUID UUID_LIFF_PSDI_SERVICE = UUID.fromString("e625601e-9e55-4597-a598-76018a0d293d");
    private static final UUID UUID_LIFF_PSDI = UUID.fromString("26e2b12b-85f0-4f3f-9fdd-91d114270e6e");
    private static final String UUID_LIFF_SERVICE_STR = "a9d158bb-9007-4fe3-b5d2-d3696a3eb067";

    private static final UUID UUID_LIFF_SERVICE = UUID.fromString(UUID_LIFF_SERVICE_STR);
    private static final UUID UUID_LIFF_WRITE = UUID.fromString("52dc2801-7e98-4fc2-908a-66161b5959b0");
    private static final UUID UUID_LIFF_READ = UUID.fromString("52dc2802-7e98-4fc2-908a-66161b5959b0");
    private static final UUID UUID_LIFF_NOTIFY = UUID.fromString("52dc2803-7e98-4fc2-908a-66161b5959b0");
    private static final UUID UUID_LIFF_DESC = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private static final int UUID_LIFF_VALUE_SIZE = 255;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        handler = new UIHandler(this);

        mBleManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBleAdapter = mBleManager.getAdapter();

        if (mBleAdapter != null) {
            prepareBle();
        }
    }

    @Override
    public boolean handleMessage(Message message) {
        switch (message.what) {
            case UIHandler.MSG_ID_TEXT: {
                TextView txt;
                txt = (TextView) findViewById(R.id.txt_message);
                txt.setText((String) message.obj);
                return true;
            }
            case UIHandler.MSG_ID_OBJ_BASE: {
                if (message.arg1 == 1) {
                    TextView txt;
                    txt = (TextView) findViewById(message.arg2);
                    txt.setText((String) message.obj);
                }
                break;
            }
        }
        return false;
    }

    private boolean checkApplicationPermissions() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.BLUETOOTH_CONNECT,
                            Manifest.permission.BLUETOOTH_ADVERTISE
                    },
                    PERMISSION_REQUEST_CODE
            );
            return false;
        } else {
            return true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case PERMISSION_REQUEST_CODE: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    prepareBle();
                } else {
                    Toast.makeText(this, "アクセス権を許可してください。", Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    private void prepareBle() throws SecurityException{
        mBtAdvertiser = mBleAdapter.getBluetoothLeAdvertiser();
        if (mBtAdvertiser == null) {
            Toast.makeText(this, "BLE Peripheralモードが使用できません。", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!checkApplicationPermissions())
            return;

        mBtGattServer = mBleManager.openGattServer(this, mGattServerCallback);

        btPsdiService = new BluetoothGattService(UUID_LIFF_PSDI_SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY);
        mPsdiCharacteristic = new BluetoothGattCharacteristic(UUID_LIFF_PSDI, BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ);
        btPsdiService.addCharacteristic(mPsdiCharacteristic);
        mBtGattServer.addService(btPsdiService);

        try { Thread.sleep(200); }catch(Exception ex){}

        btGattService = new BluetoothGattService(UUID_LIFF_SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY);

        mBtCharacteristic1 = new BluetoothGattCharacteristic(UUID_LIFF_WRITE, BluetoothGattCharacteristic.PROPERTY_WRITE, BluetoothGattCharacteristic.PERMISSION_WRITE);
        btGattService.addCharacteristic(mBtCharacteristic1);
        mBtCharacteristic2 = new BluetoothGattCharacteristic(UUID_LIFF_READ, BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ);
        btGattService.addCharacteristic(mBtCharacteristic2);
        mNotifyCharacteristic = new BluetoothGattCharacteristic(UUID_LIFF_NOTIFY, BluetoothGattCharacteristic.PROPERTY_NOTIFY, BluetoothGattCharacteristic.PERMISSION_READ);
        btGattService.addCharacteristic(mNotifyCharacteristic);
        BluetoothGattDescriptor dataDescriptor = new BluetoothGattDescriptor(UUID_LIFF_DESC, BluetoothGattDescriptor.PERMISSION_WRITE | BluetoothGattDescriptor.PERMISSION_READ);
        mNotifyCharacteristic.addDescriptor(dataDescriptor);
        mBtGattServer.addService(btGattService);

        try { Thread.sleep(200); }catch(Exception ex){}

        startBleAdvertising();
    }

    private void startBleAdvertising() throws SecurityException{
        AdvertiseData.Builder dataBuilder = new AdvertiseData.Builder();
        dataBuilder.setIncludeTxPowerLevel(true);
        dataBuilder.addServiceUuid(ParcelUuid.fromString(UUID_LIFF_SERVICE_STR));

        AdvertiseSettings.Builder settingsBuilder = new AdvertiseSettings.Builder();
        settingsBuilder.setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED);
        settingsBuilder.setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM);
        settingsBuilder.setTimeout(0);
        settingsBuilder.setConnectable(true);

        AdvertiseData.Builder respBuilder = new AdvertiseData.Builder();
        respBuilder.setIncludeDeviceName(true);

        mBtAdvertiser.startAdvertising(settingsBuilder.build(), dataBuilder.build(), respBuilder.build(), new AdvertiseCallback(){
            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                Log.d("bleperi", "onStartSuccess");
            }
            @Override
            public void onStartFailure(int errorCode) {
                Log.d("bleperi", "onStartFailure");
                handler.sendTextMessage("BLEを開始できませんでした。");
            }
        });
    }

    private BluetoothGattServerCallback mGattServerCallback = new BluetoothGattServerCallback() {
        private byte[] psdiValue = new byte[8];
        private byte[] notifyDescValue = new byte[2];
        private byte[] charValue = new byte[UUID_LIFF_VALUE_SIZE]; /* max 512 */

        @Override
        public void onMtuChanged (BluetoothDevice device, int mtu){
            Log.d("bleperi", "onMtuChanged(" + mtu + ")");
        }

        @Override
        public void onConnectionStateChange(android.bluetooth.BluetoothDevice device, int status, int newState) {
            Log.d("bleperi", "onConnectionStateChange");

            if(newState == BluetoothProfile.STATE_CONNECTED){
                mConnectedDevice = device;
                mIsConnected = true;
                Log.d("bleperi", "STATE_CONNECTED:" + device.toString());
                handler.sendTextMessage("接続されました。");
                handler.sendUIMessage(UIHandler.MSG_ID_OBJ_BASE, 1, R.id.txt_target_address, device.getAddress());
            }
            else{
                mIsConnected = false;
                Log.d("bleperi", "Unknown STATE:" + newState);
                handler.sendTextMessage("切断されました。");
                handler.sendUIMessage(UIHandler.MSG_ID_OBJ_BASE, 1, R.id.txt_target_address, "");
            }
        }

        @SuppressLint("MissingPermission")
        public void onCharacteristicReadRequest(android.bluetooth.BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic){
            Log.d("bleperi", "onCharacteristicReadRequest");

            if( characteristic.getUuid().compareTo(UUID_LIFF_PSDI) == 0) {
                mBtGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, psdiValue);
            }else if( characteristic.getUuid().compareTo(UUID_LIFF_READ) == 0){
                handler.sendUIMessage(UIHandler.MSG_ID_OBJ_BASE, 1, R.id.txt_access, "Read");
                handler.sendUIMessage(UIHandler.MSG_ID_OBJ_BASE, 1, R.id.txt_offset, Integer.toString(offset));
                handler.sendUIMessage(UIHandler.MSG_ID_OBJ_BASE, 1, R.id.txt_length, "");
                handler.sendUIMessage(UIHandler.MSG_ID_OBJ_BASE, 1, R.id.txt_value, "");

                if( offset > charValue.length ) {
                    mBtGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null);
                }else {
                    byte[] value = new byte[charValue.length - offset];
                    System.arraycopy(charValue, offset, value, 0, value.length);
                    mBtGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
                }
            }else{
                mBtGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null );
            }
        }

        @SuppressLint("MissingPermission")
        public void onCharacteristicWriteRequest(android.bluetooth.BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            Log.d("bleperi", "onCharacteristicWriteRequest");

            if( characteristic.getUuid().compareTo(UUID_LIFF_WRITE) == 0 ){
                handler.sendUIMessage(UIHandler.MSG_ID_OBJ_BASE, 1, R.id.txt_access, "Write");
                handler.sendUIMessage(UIHandler.MSG_ID_OBJ_BASE, 1, R.id.txt_offset, Integer.toString(offset));
                handler.sendUIMessage(UIHandler.MSG_ID_OBJ_BASE, 1, R.id.txt_length, Integer.toString(value.length));
                handler.sendUIMessage(UIHandler.MSG_ID_OBJ_BASE, 1, R.id.txt_value, MainActivity.toHexString(value));

                if(offset < charValue.length ) {
                    int len = value.length;
                    if( (offset + len ) > charValue.length)
                        len = charValue.length - offset;
                    System.arraycopy(value, 0, charValue, offset, len);
                    mBtGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null);
                }else {
                    mBtGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null);
                }

                if( (notifyDescValue[0] & 0x01) != 0x00 ) {
                    if (offset == 0 && value[0] == (byte) 0xff) {
                        mNotifyCharacteristic.setValue(charValue);
                        mBtGattServer.notifyCharacteristicChanged(mConnectedDevice, mNotifyCharacteristic, false);
                        handler.sendTextMessage("Notificationしました。");
                    }
                }
            }else{
                mBtGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null);
            }
        }

        @SuppressLint("MissingPermission")
        public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattDescriptor descriptor) {
            Log.d("bleperi", "onDescriptorReadRequest");

            if( descriptor.getUuid().compareTo(UUID_LIFF_DESC) == 0 ) {
                mBtGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, notifyDescValue);
            }
        }

        @SuppressLint("MissingPermission")
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            Log.d("bleperi", "onDescriptorWriteRequest");

            if( descriptor.getUuid().compareTo(UUID_LIFF_DESC) == 0 ) {
                notifyDescValue[0] = value[0];
                notifyDescValue[1] = value[1];

                mBtGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null);
            }
        }
    };

    public static String toHexString(byte[] data) {
        StringBuffer sb = new StringBuffer();
        for (byte b : data) {
            String s = Integer.toHexString(0xff & b);
            if (s.length() == 1)
                sb.append("0");
            sb.append(s);
        }
        return sb.toString();
    }
}