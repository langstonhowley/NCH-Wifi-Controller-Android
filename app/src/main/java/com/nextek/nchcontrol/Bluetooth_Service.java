/*******************************************************************************
 * Copyright 2019 Nextek Power Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.nextek.nchcontrol;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.UUID;


public class Bluetooth_Service extends Service {
    private final IBinder mBinder = new LocalBinder();
    private GlobalState gs;

    private BluetoothAdapter ba;
    private BluetoothManager bm;
    private BluetoothSocket bs;

    ConnectThread ct;
    MessageThread mt;

    private final String TAG = "Bluetooth Service";
    private final String mUUID = "00001101-0000-1000-8000-00805f9b34fb";

    private boolean connected, failed = false;

    private String nch_return;


    class LocalBinder extends Binder {
        Bluetooth_Service getService() {
            return Bluetooth_Service.this;
        }
    }

    public IBinder onBind(Intent intent) {
        //Log.e(TAG, "onBind: Returning mBinder");
        gs = (GlobalState) getApplicationContext();
        return mBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        if (bm == null && ba == null) {
            bm = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);

            if (bm != null) {
                ba = bm.getAdapter();
            } else {
                Log.e(TAG, "onCreate: Bluetooth Manager is null", null);
            }
        }

    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        disconnect();
    }

    private class ConnectThread extends Thread {

        ConnectThread() {
            try {
                bs = gs.getDevice().createRfcommSocketToServiceRecord(UUID.fromString(mUUID));

                if (bs == null) {
                    Log.e(TAG, "ConnectThread: BLUETOOTH SOCKET IS NULL", null);
                }
            } catch (Exception e) {
                Log.e(TAG, "ConnectThread: creation of socket failed: ", e);
            }
        }

        public void run() {
            try {
                if (bs == null) {
                    Log.e(TAG, "run: BLUETOOTH SOCKET IS NULL", null);
                }

                bs.getClass().getMethod("connect").invoke(bs);
                connected = true;
                Log.e(TAG, "run: Connected to device", null);

                mt = new MessageThread();
                mt.start();
            } catch (Exception e) {
                Log.e(TAG, "run: Exception occurred: ", e);

                sendBroadcast(new Intent("com.example.nchcontrol.DISCONNECTED"));
                failed = true;

                try {
                    bs.close();
                } catch (Exception e1) {
                    Log.e(TAG, "run: Failed to close socket: ", e1);
                }
            }
        }
    }

    public class MessageThread extends Thread {
        private InputStream is;
        private OutputStream os;

        MessageThread() {
            try {
                is = bs.getInputStream();
                os = bs.getOutputStream();
            } catch (Exception e) {
                Log.e(TAG, "MessageThread: Creation of one of the streams failed: ", e);
            }
        }

        public void run() {
            sendBroadcast(new Intent("com.example.nchcontrol.CONNECTED"));
            int bytes;

            while (true) {
                try {
                    byte[] buffer = new byte[256];
                    bytes = is.read(buffer);

                    String s = new String(buffer, 0, bytes);

                    if (!s.equals("CONNECTED")) {
                        nch_return = s;
                    }

                    Log.e(TAG, "run() returned: " + nch_return, null);

                } catch (Exception e) {
                    Log.e(TAG, "MessageThread run: Failed to read: ", e);
                    break;
                }
            }
        }

        void write(byte[] bytes) {
            try {
                os.write(bytes);
                os.flush();
            } catch (Exception e) {
                Log.e(TAG, "MessageThread write: Failed to write: ", e);
            }
        }
    }


    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public void search() {
        Log.e(TAG, "search: Starting search", null);
        ba.startDiscovery();
    }

    public void stopSearch() {
        Log.e(TAG, "stopSearch: Stopping Search", null);
        ba.cancelDiscovery();
    }

    @SuppressLint("StaticFieldLeak")
    @TargetApi(Build.VERSION_CODES.KITKAT)
    public void pair() {
        final boolean[] paired = {false};

        try {
            for (int i = 0; i < ba.getBondedDevices().size(); i++) {
                if (gs.getDevice().equals(ba.getBondedDevices().toArray()[i])) {
                    paired[0] = true;
                    Log.e(TAG, "connect: Device already paired", null);
                    connect();
                }
            }
            if (!paired[0]) {
                Method m = BluetoothDevice.class.getMethod("createBond", (Class[]) null);
                m.invoke(gs.getDevice(), (Object[]) null);

                if (gs.getDevice().getBondState() == BluetoothDevice.BOND_BONDED) {
                    Log.e(TAG, "pair: Device paired.", null);
                    failed = false;
                } else {
                    Log.e(TAG, "pair: Device failed to pair", null);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "pair: Exception triggered: ", e);
        }

    }

    public void connect() {
        sendBroadcast(new Intent("com.example.nchcontrol.CONNECTING"));
        ct = new ConnectThread();
        ct.start();
    }

    public void disconnect() {
        try {
            if (bs != null) {
                bs.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        failed = false;
        sendBroadcast(new Intent("com.example.nchcontrol.DISCONNECTED"));

    }

    public void sendMessage(String message) {
        Log.e(TAG, "sendMessage: SENDING " + message + " TO THE NCH.", null);
        mt.write(message.getBytes());
    }

    public String getNch_return() {
        if (nch_return == null || nch_return.equals("null")) {
            return null;
        }

        Log.e(TAG, "getNch_return: SENDING VALUE TO MAIN: " + nch_return, null);
        return nch_return;
    }

    public void clearNch_return() {
        Log.e(TAG, "clearNch_return: CLEARED", null);
        nch_return = null;
    }


    public boolean getFailed() {
        return failed;
    }

    public void resetFailed() {
        failed = false;
    }

    public BluetoothAdapter getBa() {
        return ba;
    }

}
