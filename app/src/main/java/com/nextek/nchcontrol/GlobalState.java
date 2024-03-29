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
