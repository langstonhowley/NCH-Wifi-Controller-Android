# NCH Wifi Controller (For Android)

This Android application allows users to connect to a Nextek NCH and toggle its wifi.

More specifically, a virtual serial port is made between the device and a user-selected NCH using the Bluetooth RFCOMM Protocol (more information on RFCOMM [here](https://en.wikipedia.org/wiki/List_of_Bluetooth_protocols#RFCOMM)). This allows for the user to toggle wifi by sending byte data through the serial port.

## Bluetooth Handling

Every Bluetooth event is handled by the [Bluetooth_Service Class](app/src/main/java/com/nextek/nchcontrol/Bluetooth_Service.java). Important bluetooth events include:

### Device Search

Device searching is handled by this code (found in the [Bluetooth_Service Class](app/src/main/java/com/nextek/nchcontrol/Bluetooth_Service.java)): 
```java
public void search() {
      Log.e(TAG, "search: Starting search", null);
      ba.startDiscovery();
}

public void stopSearch() {
      Log.e(TAG, "stopSearch: Stopping Search", null);
      ba.cancelDiscovery();
}
```
When a device is found a ```BroadcastReceiver``` located in the [Main Activity Class](app/src/main/java/com/nextek/nchcontrol/MainActivity.java) is triggered.

### Device Pairing (Bonding)

Device pairing is handled by this code (found in the [Bluetooth_Service Class](app/src/main/java/com/nextek/nchcontrol/Bluetooth_Service.java)): 
```java
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
                //PAIRING OCCURS HERE
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
```
When a device pairs or fails to pair a ```BroadcastReceiver``` located in the [Main Activity Class](app/src/main/java/com/nextek/nchcontrol/MainActivity.java) is triggered.
