# NCH Wifi Controller (For Android)

This Android application allows users to connect to a Nextek NCH and toggle its wifi.

More specifically, a virtual serial port is made between the device and a user-selected NCH using the Bluetooth RFCOMM Protocol (more information on RFCOMM [here](https://en.wikipedia.org/wiki/List_of_Bluetooth_protocols#RFCOMM)). This allows for the user to toggle wifi by sending byte data through the serial port.

## Bluetooth Handling

Every Bluetooth event is handled by the [Bluetooth_Service Class](app/src/main/java/com/nextek/nchcontrol/Bluetooth_Service.java). Important bluetooth events include:

### Device Search

Device searching is handled by this code (found [here](https://github.com/langstonhowley/NCH-Wifi-Controller-Android/blob/db0c4b6ab6c7e8fd87adac0f9b1f345f157d6bff/app/src/main/java/com/nextek/nchcontrol/Bluetooth_Service.java#L185) in repo): 
```java
//Creating an instance of type BluetoothAdapter
BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

//Starting Discovery
bluetoothAdapter.startDiscovery();

//Ending Discovery (manually)
bluetoothAdapter.cancelDiscovery();
```

When a device search begins [this ```BroadcastReceiver```](https://github.com/langstonhowley/NCH-Wifi-Controller-Android/blob/db0c4b6ab6c7e8fd87adac0f9b1f345f157d6bff/app/src/main/java/com/nextek/nchcontrol/MainActivity.java#L276) is triggered.

When a device is found [this ```BroadcastReceiver```](https://github.com/langstonhowley/NCH-Wifi-Controller-Android/blob/db0c4b6ab6c7e8fd87adac0f9b1f345f157d6bff/app/src/main/java/com/nextek/nchcontrol/MainActivity.java#L244) is triggered.

### Device Pairing (Bonding)

The Android phone and NCH must be bonded before connection:
> "Note: If the two devices have not been previously paired, then the Android framework automatically shows a pairing request notification or dialog to the user during the connection procedure, as shown in Figure 3..."

(See the [Android SDK Bluetooth Overview](https://developer.android.com/guide/topics/connectivity/bluetooth#ConnectionTechniques) for more information about this.)

Although a pairing request is shown automatically if the devices aren't paired, I chose to implement it to be explicit.

Device pairing is handled by this code (found [here](https://github.com/langstonhowley/NCH-Wifi-Controller-Android/blob/db0c4b6ab6c7e8fd87adac0f9b1f345f157d6bff/app/src/main/java/com/nextek/nchcontrol/Bluetooth_Service.java#L197) in repo): 
```java
Method m = BluetoothDevice.class.getMethod("createBond", (Class[]) null);
m.invoke(/* instance of the BluetoothDevice class */, (Object[]) null);
```
When a device pairs or fails to pair [this ```BroadcastReceiver```](https://github.com/langstonhowley/NCH-Wifi-Controller-Android/blob/db0c4b6ab6c7e8fd87adac0f9b1f345f157d6bff/app/src/main/java/com/nextek/nchcontrol/MainActivity.java#L317) is triggered. 

### Device Connection

Device connection is handled by this code (found [here](https://github.com/langstonhowley/NCH-Wifi-Controller-Android/blob/db0c4b6ab6c7e8fd87adac0f9b1f345f157d6bff/app/src/main/java/com/nextek/nchcontrol/Bluetooth_Service.java#L95) in repo): 
```java
//Create an RFCOMM Socket
BluetoothSocket bluetoothSocket = 
      /* instance of the BluetoothDevice class */.createRfcommSocketToServiceRecord(UUID.fromString(mUUID));
      
//Call the connect() method on that socket
BluetoothSocket.class.getMethod("connect").invoke(bluetoothSocket);
```
When a device connects or fails to connect [this ```BroadcastReceiver```](https://github.com/langstonhowley/NCH-Wifi-Controller-Android/blob/db0c4b6ab6c7e8fd87adac0f9b1f345f157d6bff/app/src/main/java/com/nextek/nchcontrol/MainActivity.java#L373) is triggered.

### Message I/O

Sending byte data through the serial port (found [here](https://github.com/langstonhowley/NCH-Wifi-Controller-Android/blob/db0c4b6ab6c7e8fd87adac0f9b1f345f157d6bff/app/src/main/java/com/nextek/nchcontrol/Bluetooth_Service.java#L136) in repo): 
```java
//Create an InputStream and an OutputStream from the connected BluetoothSocket
InputStream inputStream = bluetoothSocket.getInputStream();
OutputStream outputStream = bluetoothSocket.getOutputStream();

//Reading bytes
byte[] buffer = new byte[256]
int bytesRead = inputStream.read(buffer) //Places bytesRead amount of bytes into
                                         //buffer

//Writing bytes
outputStream.wirte("Hello World!".getBytes());
outputStream.flush();
```
