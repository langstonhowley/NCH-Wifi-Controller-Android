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

import android.bluetooth.BluetoothDevice;
import android.widget.Button;
import android.widget.TextView;

public class List_Item {
    private String name_text, address_text, btn_text;
    private Button btn;
    private TextView addy, name;
    private BluetoothDevice btd;

    public List_Item(BluetoothDevice btd) {
        name_text = btd.getName();
        address_text = btd.getAddress();
        btn_text = "Connect";
        this.btd = btd;
    }

    public void setBtn(Button btn) {
        this.btn = btn;
    }

    public Button getBtn() {
        return btn;
    }

    public TextView getAddy_view() {
        return addy;
    }

    public void setAddy_view(TextView addy) {
        this.addy = addy;
    }

    public TextView getName_view() {
        return name;
    }

    public void setName_view(TextView name) {
        this.name = name;
    }

    public String getName_text() {
        return name_text;
    }

    public void setName_text(String name_text) {
        this.name_text = name_text;
    }

    public String getAddress_text() {
        return address_text;
    }

    public void setAddress_text(String address_text) {
        this.address_text = address_text;
    }

    public String getBtn_text() {
        return btn_text;
    }

    public void setBtn_text(String btn_text) {
        this.btn_text = btn_text;
    }

    public BluetoothDevice getBtd() {
        return btd;
    }

}
