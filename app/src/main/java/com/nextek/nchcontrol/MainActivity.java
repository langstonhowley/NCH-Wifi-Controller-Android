/*******************************************************************************
 * Copyright (c) 2019. Nextek Power Systems.
 * All rights reserved.
 ******************************************************************************/

package com.nextek.nchcontrol;

import android.animation.LayoutTransition;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;


/**
 * The MainActivity Class.
 */
public class MainActivity extends AppCompatActivity {
    ArrayList<BluetoothDevice> nch_list = new ArrayList<>();    //An arraylist to hold the nchs found during search.
    ArrayList<List_Item> list_items = new ArrayList<>();

    Custom_Adapter adapter;                                     //The array adapter that inflates the list of nch_list
    Bluetooth_Service service;                                  //The bluetooth service

    final Animation fade_in = new AlphaAnimation(0.0f, 1.0f);   //The fade in animation
    final Animation fade_out = new AlphaAnimation(1.0f, 0.0f);  //The fade out animation

    ArrayList<Boolean> hasBeenAnimated = new ArrayList<>();     //This keeps track of what ListView items have been animated
    //so no item is animated twice in the UI

    int[] animation_counters = {0, 0, 0};                         //Animation counters count how many times an animation occurs
    //The first counter is for how many times the fade out animation occurs
    //The second is for how many time the fade in animation occurs
    //The last is to indicate whether an animation was cancelled
    int child_at = 0;
    int wifi_ = 0, prev_wifi_state = 1000;

    ProgressBar pb;                                             //The loading spinner

    private final String TAG = "Main Activity";                 //For debugging purposes

    boolean bound = false;                                      //Keeps track of if the main activity is bound to the bluetooth service
    boolean scanning = false;                                   //Keeps track of if the bluetooth service is scanning for nch_list
    boolean search_man_cancelled = false;                       //If the scan (or search) is manually cancelled, this is true;

    GlobalState globalState;                                    //An object where all the global variables are held

    SwipeRefreshLayout main_layout;                             //The layout holding the list of nchs
    LinearLayout loading_layout;                                //The layout holding the loading text and loading spinner

    ArrayList<BroadcastReceiver> receivers = new ArrayList<>(); //All of the Broadcast receivers in this class


    ThreadPoolExecutor tpe = (ThreadPoolExecutor) Executors.newFixedThreadPool(10);

    Toolbar toolbar;
    Menu menu;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.e(TAG, "onCreate: Created", null);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        //Setup the Toolbar at the top of the page

        toolbar = findViewById(R.id.toolbar);
        toolbar.setSubtitle("");
        setSupportActionBar(toolbar);
        toolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if (item.getItemId() == R.id.disconnect) {
                    sendBroadcast(new Intent("com.example.nchcontrol.DISCONNECTING"));

                    service.disconnect();
                } else if (item.getItemId() == R.id.help) {
                    popup_help();
                }
                return false;
            }
        });

        finish_init();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        this.menu = menu;

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean b = super.onPrepareOptionsMenu(menu);

        View view = findViewById(R.id.help);
        if (view != null && view instanceof TextView) {
            ((TextView) view).setTextColor(Color.BLUE); // Make text colour blue
        }

        return b;
    }

    /**
     * This finishes the initialization of the Main Activity
     */
    public void finish_init() {
        main_layout = findViewById(R.id.swipe_layout);
        loading_layout = findViewById(R.id.loading);
        pb = findViewById(R.id.bar);

        //Connect to access the Global Variables
        globalState = (GlobalState) getApplicationContext();

        //Connect to the Bluetooth Service
        startService(new Intent(this, MainActivity.class));
        bindService(new Intent(this, Bluetooth_Service.class), serv_connection, Context.BIND_AUTO_CREATE);

        //The thing that adds the nchs to the list
        adapter = new Custom_Adapter(list_items, this);
        ((ListView) findViewById(R.id.nch_list)).setAdapter(adapter);

        //When the layout is refreshed this is what happens
        main_layout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                //Clear the nch list and indicate that the search was manually cancelled
                nch_list.clear();
                list_items.clear();
                hasBeenAnimated.clear();
                adapter.notifyDataSetChanged();
                search_man_cancelled = true;

                if (!scanning) {
                    //If not scanning, start scanning again.
                    scanning = true;
                    search_man_cancelled = false;
                    service.search();
                } else {
                    //Restart the scan if already scanning
                    service.stopSearch();
                    service.search();
                }

                //If this weren't here we'd be refreshing forever
                main_layout.setRefreshing(false);
            }
        });
    }

    @Override
    protected void onDestroy() {
        //This triggers hen the activity is finished or the user closes the app
        Log.e(TAG, "onDestroy: Destroyed", null);
        super.onDestroy();

        //Stop scanning
        if (scanning) {
            service.stopSearch();
        }

        //This is just a loop to unregister all receivers
        for (int i = 0; i < receivers.size(); i++) {
            unregisterReceiver(receivers.get(i));
        }

        //Unbind from the Bluetooth Service
        if (bound) {
            unbindService(serv_connection);
        }
    }

    /**
     * Add an nch to the nch_list and update the UI list.
     *
     * @param nch the nch to add
     */
    public void addNCH(BluetoothDevice nch) {
        nch_list.add(nch);
        list_items.add(new List_Item(nch));
        hasBeenAnimated.add(false);
        adapter.notifyDataSetChanged();
    }


    /**
     * This is the Broadcast Receiver that is triggered whenever a Bluetooth device is found
     * It checks whether the Bluetooth device is actually an nch and if it is, call addNCH().
     */
    private final BroadcastReceiver found = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            BluetoothDevice btd = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            //Log.e(TAG, "onReceive: Got a device called " + btd.getName() + ", with a mac add of " + btd.getAddress(), null);

            int decider = 0; //The decider variable which is set to -1 if the nch is already in the list


            if (btd != null && btd.getName() != null && btd.getName().toLowerCase().contains("nch")) {
                for (int i = 0; i < nch_list.size(); i++) {
                    //Compare the names of the nchs if a name can be found
                    if (btd.getName().equals(nch_list.get(i).getName())) {
                        decider = -1;
                        break;
                    }
                }
            } else {
                decider = -1;
            }


            if (decider == 0) {
                addNCH(btd);
            }
        }
    };

    /**
     * This is the Broadcast Receiver that is triggered when a scan is started or finished.
     */
    private final BroadcastReceiver searching = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            if (intent.getAction() != null) {
                switch (intent.getAction()) {
                    case BluetoothAdapter.ACTION_DISCOVERY_STARTED:
                        Log.e(TAG, "search onReceive: Received that the search started", null);

                        //Add the loading layout to the bottom of the screen
                        placeLoadingLayout(MainActivity.this.getResources().getString(R.string.search_string));

                        scanning = true;
                        break;
                    case BluetoothAdapter.ACTION_DISCOVERY_FINISHED:
                        Log.e(TAG, "search onReceive: Received that the search finished", null);

                        removeLoadingLayout();

                        scanning = false;

                        if (!search_man_cancelled) {
                            //If the search wasn't manually cancelled popup a message asking if the user
                            //would like to search again
                            popup_search_over();
                        }

                        search_man_cancelled = false; //Reset so it doesn't stay true forever if it is.
                        break;
                    default:
                        Log.e(TAG, "search onReceive: idek what to do with this: " + intent.getAction(), null);
                }
            }
        }
    };


    /**
     * This is the Broadcast Receiver that is triggered whenever the Bluetooth Service adapter's Bond State
     * changes.
     */
    private final BroadcastReceiver bond = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int bond = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, 0);

            if (bond == BluetoothDevice.BOND_BONDED) {
                //BONDING SUCCESSFUL
                Log.e(TAG, "bond onReceive: Bonded with nch", null);

                service.connect();
            } else if (bond == BluetoothDevice.BOND_NONE) {
                //UNBONDED FROM NCH OR PAIR FAILED
                removeLoadingLayout();

                if (service.getFailed()) {
                    popup_bond_error();
                    Log.e(TAG, "bond onReceive: Bonding failed", null);
                    service.resetFailed();
                }

                unfreeze_screen();
            } else if (bond == BluetoothDevice.BOND_BONDING) {
                Log.e(TAG, "onReceive: PAIRING", null);

                if (globalState.getDevice() != null) {
                    placeLoadingLayout("Pairing with " + globalState.getDevice().getName());
                }
                freeze_screen();
            } else {
                Log.e(TAG, "bond onReceive: idek what to do with this: " + bond, null);
            }
        }
    };

    /**
     * This is the Broadcast Receiver that is triggered in case a pair request is sent to the user's device
     */
    private final BroadcastReceiver pair_req = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            byte[] pin = "0000".getBytes();

            try {
                globalState.getDevice().getClass().getMethod("setPin", byte[].class).invoke(globalState.getDevice(), (Object) pin);

                try {
                    globalState.getDevice().getClass().getMethod("setPairingConfirmation", boolean.class).invoke(globalState.getDevice(), true);
                } catch (Exception e1) {
                    Log.e(TAG, "onReceive: Failed to set pairing confirmation: ", e1);
                }
            } catch (Exception e) {
                Log.e(TAG, "onReceive: Failed to set pin: ", e);
            }
        }
    };

    private final BroadcastReceiver connected = new BroadcastReceiver() {
        @SuppressLint("StaticFieldLeak")
        @Override
        public void onReceive(Context context, Intent intent) {

            if (intent.getAction() != null) {
                switch (intent.getAction()) {
                    case "com.example.nchcontrol.CONNECTED":
                        Log.e(TAG, "onReceive: Connected to nch.", null);

                        removeLoadingLayout();

                        toolbar.setLayoutTransition(new LayoutTransition());

                        toolbar.setTitle("Connected To: " + globalState.getDevice().getName());
                        toolbar.setTitleTextColor(MainActivity.this.getResources().getColor(R.color.nextek_green));
                        toolbar.setSubtitle("Press the Nextek Logo to Disconnect");
                        toolbar.setSubtitleTextColor(MainActivity.this.getResources().getColor(R.color.nextek_blue));
                        menu.findItem(R.id.disconnect).setVisible(true);


                        for (int i = 0; i < nch_list.size(); i++) {
                            ListView lv = findViewById(R.id.nch_list);
                            TextView t = lv.getChildAt(i).findViewById(R.id.nch_name);

                            if (globalState.getDevice().getName().contentEquals(t.getText())) {
                                child_at = i;
                            }
                        }

                        placeLoadingLayout("Getting Wifi Data");

                        new Change_Row_On_Connect().executeOnExecutor(tpe);
                        break;
                    case "com.example.nchcontrol.CONNECTING":
                        Log.e(TAG, "onReceive: Connecting to nch.", null);

                        placeLoadingLayout("Connecting to " + globalState.getDevice().getName());

                        freeze_screen();
                        break;
                    case "com.example.nchcontrol.DISCONNECTING":
                        Log.e(TAG, "onReceive: Disconnecting from nch.", null);

                        placeLoadingLayout("Disconnecting from " + globalState.getDevice().getName());

                        freeze_screen();
                        break;
                    case "com.example.nchcontrol.DISCONNECTED":
                        Log.e(TAG, "onReceive: Disconnected from nch.", null);

                        toolbar.setLayoutTransition(new LayoutTransition());
                        toolbar.setTitle("No Current Connections");
                        toolbar.setTitleTextColor(MainActivity.this.getResources().getColor(R.color.white));
                        toolbar.setSubtitle("");

                        if (menu.findItem(R.id.disconnect) != null)
                            menu.findItem(R.id.disconnect).setVisible(false);


                        wifi_ = 0;
                        prev_wifi_state = -1;

                        for (int i = 0; i < list_items.size(); i++) {
                            list_items.get(i).getName_view().setTextColor(MainActivity.this.getResources().getColor(R.color.white));
                            list_items.get(i).setAddress_text(globalState.getDevice().getAddress());
                            list_items.get(i).setBtn_text("Connect");

                            list_items.get(i).getAddy_view().setTextColor(MainActivity.this.getResources().getColor(R.color.nextek_green));
                            list_items.get(i).getBtn().setTextColor(MainActivity.this.getResources().getColor(R.color.nextek_blue));

                            list_items.get(i).getAddy_view().startAnimation(AnimationUtils.loadAnimation(MainActivity.this, android.R.anim.fade_in));
                            list_items.get(i).getBtn().startAnimation(AnimationUtils.loadAnimation(MainActivity.this, android.R.anim.fade_in));

                            list_items.get(i).getBtn().setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View view) {

                                    globalState.setDevice(list_items.get(child_at).getBtd());
                                    search_man_cancelled = true;
                                    service.stopSearch();

                                    service.pair();
                                }
                            });
                        }

                        animation_counters[animation_counters.length - 1] = 1;

                        adapter.notifyDataSetChanged();

                        removeLoadingLayout();
                        unfreeze_screen();
                        service.clearNch_return();

                        if (service.getFailed()) {
                            popup_connect_error();
                        }
                        break;
                }
            }
        }
    };

    /**
     * This connects the Bluetooth Service to this activity and handles when occurs on connection
     * and on disconnection.
     */
    private ServiceConnection serv_connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            Bluetooth_Service.LocalBinder binder = (Bluetooth_Service.LocalBinder) iBinder;
            service = binder.getService();
            bound = true;
            Log.e(TAG, "onServiceConnected: Service has been connected", null);

            registerReceivers();

            service.search();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            bound = false;
        }
    };

    /**
     * The Custom Adapter class inflates the list of nchs when one is found.
     */
    public class Custom_Adapter extends BaseAdapter implements ListAdapter {
        ArrayList<List_Item> lis;
        private Context context;

        Custom_Adapter(ArrayList<List_Item> lis, Context context) {
            this.lis = lis;
            this.context = context;
        }

        @Override
        public int getCount() {
            return lis.size();
        }

        @Override
        public Object getItem(int i) {
            return lis.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }


        @Override
        public View getView(final int position, View view, ViewGroup viewGroup) {
            View v = view;

            if (view == null) {
                LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                if (inflater != null) {
                    v = (inflater).inflate(R.layout.list_item, null);
                } else {
                    Log.e(TAG, "getView: Inflater = null", null);
                }
            }

            //Put the NCH name and address in their corresponding text views in the UI list
            List_Item li = lis.get(position);

            li.setName_view((TextView) v.findViewById(R.id.nch_name));
            li.getName_view().setText(li.getName_text());

            li.setAddy_view((TextView) v.findViewById(R.id.nch_addr));
            li.getAddy_view().setText(li.getAddress_text());

            li.setBtn((Button) v.findViewById(R.id.connect));
            li.getBtn().setText(li.getBtn_text());

            if (wifi_ == 1 && position == child_at) {
                li.getAddy_view().setTextColor(MainActivity.this.getResources().getColor(R.color.nextek_green));
                li.getBtn().setTextColor(MainActivity.this.getResources().getColor(R.color.turn_off_red));
                li.getAddy_view().startAnimation(AnimationUtils.loadAnimation(MainActivity.this, android.R.anim.fade_in));
                li.getBtn().startAnimation(AnimationUtils.loadAnimation(MainActivity.this, android.R.anim.fade_in));
            } else if (wifi_ == -1 && position == child_at) {
                li.getAddy_view().setTextColor(MainActivity.this.getResources().getColor(R.color.turn_off_red));
                li.getBtn().setTextColor(MainActivity.this.getResources().getColor(R.color.nextek_green));
                li.getAddy_view().startAnimation(AnimationUtils.loadAnimation(MainActivity.this, android.R.anim.fade_in));
                li.getBtn().startAnimation(AnimationUtils.loadAnimation(MainActivity.this, android.R.anim.fade_in));
            } else if (wifi_ != 0 && position != child_at) {
                li.getAddy_view().setTextColor(MainActivity.this.getResources().getColor(R.color.light_gray));
                li.getBtn().setTextColor(MainActivity.this.getResources().getColor(R.color.light_gray));
                li.getName_view().setTextColor(MainActivity.this.getResources().getColor(R.color.light_gray));
                li.getAddy_view().startAnimation(AnimationUtils.loadAnimation(MainActivity.this, android.R.anim.fade_in));
                li.getBtn().startAnimation(AnimationUtils.loadAnimation(MainActivity.this, android.R.anim.fade_in));
            }

            if (position != child_at && wifi_ != 0) {
                li.getBtn().setClickable(false);
            }


            //Make the last item in the last (aka the newest nch found) slide in from the right
            Animation a = AnimationUtils.loadAnimation(MainActivity.this, android.R.anim.slide_in_left);
            a.setDuration(500);
            a.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {

                }

                @Override
                public void onAnimationEnd(Animation animation) {

                }

                @Override
                public void onAnimationRepeat(Animation animation) {

                }
            });
            if (position == list_items.size() - 1) {
                if (!hasBeenAnimated.get(list_items.size() - 1)) {
                    v.startAnimation(a);
                    hasBeenAnimated.set(list_items.size() - 1, true);
                }
            }

            //When the connect button is clicked this is what happens
            if (!li.getBtn().hasOnClickListeners()) {
                lis.get(position).getBtn().setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Log.e(TAG, "onClick: Connect button clicked at: " + position, null);

                        globalState.setDevice(nch_list.get(position)); //Set the global NCH
                        search_man_cancelled = true;
                        service.stopSearch();

                        service.pair();
                    }
                });
            }
            return v;
        }
    }

    /**
     * This is the method that registers all of the Broadcast Receivers in this activity
     * No real documentation here, go to the definitions for all of the Receivers.
     */
    private void registerReceivers() {
        receivers.clear();

        IntentFilter i = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(found, i);
        receivers.add(found);

        i = new IntentFilter();
        i.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        i.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(searching, i);
        receivers.add(searching);

        i = new IntentFilter();
        i.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        i.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(bond, i);
        receivers.add(bond);

        i = new IntentFilter();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            i.addAction(BluetoothDevice.ACTION_PAIRING_REQUEST);
            registerReceiver(pair_req, i);
            receivers.add(pair_req);
        }

        i = new IntentFilter();
        i.addAction("com.example.nchcontrol.CONNECTED");
        i.addAction("com.example.nchcontrol.CONNECTING");
        i.addAction("com.example.nchcontrol.DISCONNECTING");
        i.addAction("com.example.nchcontrol.DISCONNECTED");
        registerReceiver(connected, i);
        receivers.add(connected);
    }

    /**
     * This method allows for the flashing text on certain operations.
     *
     * @param ta the TextView to animate.
     */
    public void animateText_Fade(TextView ta) {
        final TextView tv = ta;
        animation_counters[0] = 0;
        animation_counters[1] = 0;
        animation_counters[animation_counters.length - 1] = 0;

        fade_in.setDuration(1000);
        fade_out.setDuration(1000);

        tv.startAnimation(fade_out);

        fade_in.setAnimationListener(new Animation.AnimationListener() {

            @Override
            public void onAnimationStart(Animation animation) {
                //Log.e(TAG, "onAnimationStart: Started fade in with counter at " + animation_counters[0], null);
                //Log.e(TAG, "onAnimationStart: The master is at " + animation_counters[animation_counters.length-1], null);
                if (animation_counters[0] > 10000) {
                    animation_counters[animation_counters.length - 1] = 1;
                    fade_in.cancel();
                }
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                if (animation_counters[animation_counters.length - 1] != 1) {
                    fade_in.cancel();
                    animation_counters[0]++;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tv.startAnimation(fade_out);
                        }
                    });
                }
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });

        fade_out.setAnimationListener(new Animation.AnimationListener() {

            @Override
            public void onAnimationStart(Animation animation) {
                //Log.e(TAG, "onAnimationStart: Started fade out with counter at " + animation_counters[1], null);
                //Log.e(TAG, "onAnimationStart: The master is at " + animation_counters[animation_counters.length-1], null);
                if (animation_counters[1] > 10000) {
                    animation_counters[animation_counters.length - 1] = 1;
                    fade_out.cancel();
                }
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                if (animation_counters[animation_counters.length - 1] != 1) {
                    fade_out.cancel();
                    animation_counters[1]++;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tv.startAnimation(fade_in);
                        }
                    });
                }
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
    }

    /**
     * This pops up a Dialog asking if the user would like to attempt a connection again
     * when a connection attempt fails
     */
    public void popup_connect_error() {
        AlertDialog.Builder b = new AlertDialog.Builder(MainActivity.this);

        TextView titleView = new TextView(MainActivity.this);
        titleView.setGravity(Gravity.CENTER);
        titleView.setText("Connection Failed");
        titleView.setTypeface(null, Typeface.BOLD);
        titleView.setTextColor(MainActivity.this.getResources().getColor(R.color.white));
        titleView.setTextSize(30);
        titleView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        b.setCustomTitle(titleView);


        b.setMessage("Bluetooth connection with " + globalState.getDevice().getName() + " failed.\n" +
                "Attempt another connection?");
        b.setCancelable(false);
        b.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                service.pair();
                dialogInterface.cancel();
            }
        });

        b.setNegativeButton("No", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.cancel();
            }
        });

        b.setCancelable(false);

        final AlertDialog alert = b.create();

        MainActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                alert.show();
            }
        });

        Window dialog = alert.getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && dialog != null) {
            dialog.setBackgroundDrawable(MainActivity.this.getDrawable(R.color.background_gray));
        }

        Button positive = alert.getButton(AlertDialog.BUTTON_POSITIVE);
        Button negative = alert.getButton(AlertDialog.BUTTON_NEGATIVE);

        positive.setTextColor(MainActivity.this.getResources().getColor(R.color.nextek_blue));
        negative.setTextColor(MainActivity.this.getResources().getColor(R.color.nextek_blue));

        LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) positive.getLayoutParams();
        layoutParams.weight = 10;

        positive.setLayoutParams(layoutParams);
        negative.setLayoutParams(layoutParams);

        TextView message = alert.findViewById(android.R.id.message);
        message.setTextColor(MainActivity.this.getResources().getColor(R.color.nextek_green));
        message.setGravity(Gravity.CENTER);

    }

    /**
     * This pops up a Dialog asking if the user would like to attempt a pair again
     * when a pairing attempt fails
     */
    public void popup_bond_error() {
        AlertDialog.Builder b = new AlertDialog.Builder(MainActivity.this);
        //b.setTitle("Pairing With " + globalState.getDevice().getName() + " Failed.");
        TextView titleView = new TextView(MainActivity.this);
        titleView.setGravity(Gravity.CENTER);
        titleView.setText(String.format("Pairing With %s Failed", globalState.getDevice().getName()));
        titleView.setTypeface(null, Typeface.BOLD);
        titleView.setTextColor(MainActivity.this.getResources().getColor(R.color.white));
        titleView.setTextSize(30);
        titleView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        b.setCustomTitle(titleView);

        b.setMessage("Would You Like To Attempt To Pair Again?");

        b.setCancelable(false);

        b.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.cancel();
                service.pair();
            }
        });

        b.setNegativeButton("No", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.cancel();
            }
        });

        final AlertDialog alert = b.create();

        MainActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                alert.show();
            }
        });

        Window dialog = alert.getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && dialog != null) {
            dialog.setBackgroundDrawable(MainActivity.this.getDrawable(R.color.background_gray));
        }

        Button positive = alert.getButton(AlertDialog.BUTTON_POSITIVE);
        Button negative = alert.getButton(AlertDialog.BUTTON_NEGATIVE);

        positive.setTextColor(MainActivity.this.getResources().getColor(R.color.nextek_blue));
        negative.setTextColor(MainActivity.this.getResources().getColor(R.color.nextek_blue));

        LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) positive.getLayoutParams();
        layoutParams.weight = 10;

        positive.setLayoutParams(layoutParams);
        negative.setLayoutParams(layoutParams);

        TextView message = alert.findViewById(android.R.id.message);
        message.setTextColor(MainActivity.this.getResources().getColor(R.color.nextek_green));
        message.setGravity(Gravity.CENTER);


        search_man_cancelled = false;
    }

    /**
     * This pops up a message when a Bluetooth scan ends (aka it times out)
     */
    public void popup_search_over() {
        AlertDialog.Builder b = new AlertDialog.Builder(MainActivity.this);

        TextView titleView = new TextView(MainActivity.this);
        titleView.setGravity(Gravity.CENTER);
        titleView.setText(R.string.search_complete);
        titleView.setTypeface(null, Typeface.BOLD);
        titleView.setTextColor(MainActivity.this.getResources().getColor(R.color.white));
        titleView.setTextSize(30);
        titleView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        b.setCustomTitle(titleView);

        b.setMessage("Would You Like To Search Again?");

        b.setCancelable(false);

        b.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                nch_list.clear();
                list_items.clear();
                adapter.notifyDataSetChanged();
                service.search();
            }
        });
        b.setNegativeButton("No", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.cancel();
            }
        });

        if (!scanning) {
            final AlertDialog alert = b.create();

            MainActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    alert.show();
                }
            });


            Window dialog = alert.getWindow();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && dialog != null) {
                dialog.setBackgroundDrawable(MainActivity.this.getDrawable(R.color.background_gray));
            }

            Button positive = alert.getButton(AlertDialog.BUTTON_POSITIVE);
            Button negative = alert.getButton(AlertDialog.BUTTON_NEGATIVE);

            positive.setTextColor(MainActivity.this.getResources().getColor(R.color.nextek_blue));
            negative.setTextColor(MainActivity.this.getResources().getColor(R.color.nextek_blue));

            LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) positive.getLayoutParams();
            layoutParams.weight = 10;

            positive.setLayoutParams(layoutParams);
            negative.setLayoutParams(layoutParams);

            TextView message = alert.findViewById(android.R.id.message);
            message.setTextColor(MainActivity.this.getResources().getColor(R.color.nextek_green));
            message.setGravity(Gravity.CENTER);


            search_man_cancelled = false;
        }
    }

    public void popup_help() {
        AlertDialog.Builder b = new AlertDialog.Builder(MainActivity.this);
        //b.setTitle("Pairing With " + globalState.getDevice().getName() + " Failed.");
        TextView titleView = new TextView(MainActivity.this);
        titleView.setGravity(Gravity.CENTER);
        titleView.setText(String.format("Help"));
        titleView.setTypeface(null, Typeface.BOLD);
        titleView.setTextColor(MainActivity.this.getResources().getColor(R.color.white));
        titleView.setTextSize(30);
        titleView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        b.setCustomTitle(titleView);

        String s1 = "-To begin a search for NCHs swipe down in the middle of the screen.";
        String s2 = "-To pop up this menu press 'Help' int the upper right corner of the screen.";
        String s3 = "-To make a connection to an NCH, press the button with the NCH's name on it.";
        String s4 = "-Once connected, press the button with the title 'Turn Wifi: ' to toggle the NCH's wifi.";
        String s5 = "-To disconnect press the Nextek logo at the top right of the screen.";

        b.setMessage(s1 + "\n\n" + s2 + "\n\n" + s3 + "\n\n" + s4 + "\n\n" + s5);

        b.setCancelable(false);

        b.setNeutralButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.cancel();
            }
        });

        final AlertDialog alert = b.create();

        MainActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                alert.show();
            }
        });

        Window dialog = alert.getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && dialog != null) {
            dialog.setBackgroundDrawable(MainActivity.this.getDrawable(R.color.background_gray));
        }

        Button neutral = alert.getButton(AlertDialog.BUTTON_NEUTRAL);

        neutral.setTextColor(MainActivity.this.getResources().getColor(R.color.nextek_blue));

        LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) neutral.getLayoutParams();
        layoutParams.weight = 10;

        neutral.setLayoutParams(layoutParams);

        TextView message = alert.findViewById(android.R.id.message);
        message.setTextColor(MainActivity.this.getResources().getColor(R.color.nextek_green));
        message.setGravity(Gravity.CENTER);
    }

    /**
     * Whenever a significant operation is occurring the loading layout is placed at the bottom of the screen
     * to indicate that there's something happening.
     *
     * @param message the message to display
     */
    public void placeLoadingLayout(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                main_layout.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 7));
                loading_layout.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
                ((TextView) findViewById(R.id.loading_message_display)).setText(message);
                animateText_Fade(((TextView) findViewById(R.id.loading_message_display)));

                LayoutTransition lt = new LayoutTransition();
                lt.setDuration(LayoutTransition.APPEARING, 3);

                main_layout.setLayoutTransition(new LayoutTransition());
                loading_layout.setLayoutTransition(lt);
            }
        });
    }

    /**
     * This removes the loading layout from the bottom of the screen.
     */
    public void removeLoadingLayout() {
        main_layout.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 8));
        if (findViewById(R.id.loading_message_display).getAnimation() != null) {
            findViewById(R.id.loading_message_display).getAnimation().cancel();
        }
        loading_layout.setLayoutParams(new LinearLayout.LayoutParams(0, 0, 0));

        LayoutTransition lt = new LayoutTransition();
        lt.setDuration(LayoutTransition.DISAPPEARING, 3);

        main_layout.setLayoutTransition(new LayoutTransition());
        loading_layout.setLayoutTransition(lt);
    }

    /**
     * This method is called on an attempt to connect so that nothing weird can happen during connection
     */
    public void freeze_screen() {
        //Just ensuring that no other connect button can be clicked during an attempt to connect
        for (int i = 0; i < nch_list.size(); i++) {
            try {
                list_items.get(i).getBtn().setClickable(false);
            } catch (Exception e) {
                Log.e(TAG, "freeze_screen: null at " + i, e);
            }
        }

        //Ensuring that the main layout can't be refreshed during an attempt to connect
        main_layout.setRefreshing(false);
        main_layout.setEnabled(false);
    }

    /**
     * The opposite of freeze_screen
     */
    public void unfreeze_screen() {
        for (int i = 0; i < nch_list.size(); i++) {
            try {
                list_items.get(i).getBtn().setClickable(true);
            } catch (Exception e) {
                Log.e(TAG, "freeze_screen: null at " + i, e);
            }
        }

        //Ensuring that the main layout can't be refreshed during an attempt to connect
        main_layout.setEnabled(true);
        main_layout.setRefreshing(false);
    }

    @SuppressLint("StaticFieldLeak")
    public void change_row_on_connect() {

        service.sendMessage("Get_Wifi");
        String wifi_status;
        final boolean[] isFinished = {false};

        final int[] i = {0};
        final String[] ret = new String[1];

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {

                while (!isFinished[0]) {
                    while (service.getNch_return() == null) {
                        i[0]++;
                    }

                    Log.e(TAG, "doInBackground: Value received is " + Integer.parseInt(service.getNch_return()), null);
                    if (Integer.parseInt(service.getNch_return()) != prev_wifi_state) {
                        ret[0] = service.getNch_return();
                        Log.e(TAG, "getNCHMessage: ret[0] is " + ret[0] + ". FINISHED.", null);

                        synchronized (isFinished) {
                            Log.e(TAG, "doInBackground: NOTIFYING", null);
                            isFinished.notify();
                            isFinished[0] = true;
                        }
                    } else {
                        service.clearNch_return();
                    }
                }

                return null;
            }
        }.executeOnExecutor(tpe);


        synchronized (isFinished) {
            try {
                Log.e(TAG, "change_row_on_connect: WAITING...", null);
                isFinished.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        Log.e(TAG, "change_row_on_connect: Moving on out of the loop", null);

        wifi_status = ret[0];
        Log.e(TAG, "change_row_on_connect: Set wifi status to " + wifi_status, null);
        service.clearNch_return();


        if (wifi_status.equals("0")) {
            prev_wifi_state = 0;
            Log.e(TAG, "change_row_on_connect: Changing lower text color to OFF", null);
            list_items.get(child_at).setAddress_text("Wifi Status: OFF");
            list_items.get(child_at).setBtn_text("Turn Wifi ON");

            wifi_ = -1;

            list_items.get(child_at).getBtn().setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Log.e(TAG, "onClick: TURNING ON WIFI", null);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            placeLoadingLayout("Turning Wifi On");
                        }
                    });

                    service.clearNch_return();
                    service.sendMessage("Wifi_Enable");


                    new Change_Row_On_Connect().executeOnExecutor(tpe);
                }
            });
        } else if (wifi_status.equals("1")) {
            prev_wifi_state = 1;
            Log.e(TAG, "change_row_on_connect: Changing lower text color to ON", null);

            list_items.get(child_at).setAddress_text("Wifi Status: ON");
            list_items.get(child_at).setBtn_text("Turn Wifi OFF");

            wifi_ = 1;

            list_items.get(child_at).getBtn().setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Log.e(TAG, "onClick: TURNING WIFI OFF", null);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            placeLoadingLayout("Turning Wifi Off");
                        }
                    });

                    service.clearNch_return();
                    service.sendMessage("Wifi_Disable");


                    new Change_Row_On_Connect().executeOnExecutor(tpe);
                }
            });
        }

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                removeLoadingLayout();
                Log.e(TAG, "run: About to tell the adapter notify state changed", null);
                adapter.notifyDataSetChanged();
            }
        });
    }

    public class Change_Row_On_Connect extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... voids) {
            change_row_on_connect();
            return null;
        }
    }


}
