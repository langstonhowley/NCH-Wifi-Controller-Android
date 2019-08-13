/*******************************************************************************
 * Copyright (c) 2019. Nextek Power Systems.
 * All rights reserved.
 ******************************************************************************/

package com.nextek.nchcontrol;

import android.app.Application;
import android.bluetooth.BluetoothDevice;

public class GlobalState extends Application {
    private BluetoothDevice bd;

    public GlobalState() {

    }

    public void setDevice(BluetoothDevice btd) {
        bd = btd;
    }

    public BluetoothDevice getDevice() {
        return bd;
    }

}
