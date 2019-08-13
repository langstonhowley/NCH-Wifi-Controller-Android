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

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStatusCodes;

import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

public class SplashScreen extends AppCompatActivity {
    Handler handler;
    boolean bound;

    Bluetooth_Service service;

    private final int REQUEST_BLUETOOTH_CODE = 3;
    private final int REQUEST_BLUETOOTH_ADMIN_CODE = 4;
    private final int REQUEST_COARSE_LOCATION_CODE = 5;
    private final int REQUEST_FINE_LOCATION_CODE = 6;
    private final int REQUEST_BLUETOOTH_PRIV_CODE = 7;
    private ArrayList<Integer> perms = new ArrayList<>();

    private final String TAG = "Splash Screen";

    ThreadPoolExecutor tpe = (ThreadPoolExecutor) Executors.newFixedThreadPool(10);
    WaitUntilPerms w = new WaitUntilPerms();
    FindLoc f = new FindLoc();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.splash_screen);

        startService(new Intent(this, SplashScreen.class));
        bindService(new Intent(this, Bluetooth_Service.class), serv_connection, Context.BIND_AUTO_CREATE);

        w.executeOnExecutor(tpe);
    }


    private ServiceConnection serv_connection = new ServiceConnection() {
        @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            Bluetooth_Service.LocalBinder binder = (Bluetooth_Service.LocalBinder) iBinder;
            service = binder.getService();
            bound = true;
            Log.e(TAG, "onServiceConnected: Service has been connected", null);

            Log.e(TAG, "onServiceConnected: Executing the async task", null);
            f.executeOnExecutor(tpe);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            bound = false;
        }
    };

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 1) {
            Log.e(TAG, "onActivityResult: Got a response to Location", null);
            if (resultCode == Activity.RESULT_OK) {
                perms.add(0);
            } else {
                popup_loc_enable(SplashScreen.this);
            }
        } else if (requestCode == 0) {
            Log.e(TAG, "onActivityResult: Got a response to Bluetooth", null);
            if (resultCode == Activity.RESULT_OK) {
                perms.add(0);
            } else {
                popup_bt_enable(SplashScreen.this);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case REQUEST_BLUETOOTH_CODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.e(TAG, "onRequestPermissionsResult: Bluetooth is on", null);
                } else {
                    Log.e(TAG, "onRequestPermissionsResult: Bluetooth is off", null);
                }
                break;
            case REQUEST_BLUETOOTH_ADMIN_CODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.e(TAG, "onRequestPermissionsResult: Bluetooth Admin is on", null);
                } else {
                    Log.e(TAG, "onRequestPermissionsResult: Blue Admin is off", null);
                }
                break;
            case REQUEST_COARSE_LOCATION_CODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.e(TAG, "onRequestPermissionsResult: Coarse Location is on", null);
                } else {
                    Log.e(TAG, "onRequestPermissionsResult: Coarse Loc is off", null);
                }
                break;
            case REQUEST_FINE_LOCATION_CODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.e(TAG, "onRequestPermissionsResult: Fine Location is on", null);
                } else {
                    Log.e(TAG, "onRequestPermissionsResult: Fine Loc is off", null);
                }
                break;
            case REQUEST_BLUETOOTH_PRIV_CODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.e(TAG, "onRequestPermissionsResult: Priv is on", null);
                } else {
                    Log.e(TAG, "onRequestPermissionsResult: Priv is off", null);
                }
                break;
        }
    }

    public void popup_bt_enable(Activity act) {
        AlertDialog.Builder b = new AlertDialog.Builder(act);
        b.setTitle("You Must Enable Bluetooth To Use This App");
        b.setMessage("Press The OK Button To Activate Bluetooth");

        b.setCancelable(false);

        b.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.cancel();
                startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), 1);
            }
        });


        final AlertDialog alert = b.create();

        act.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                alert.show();
            }
        });
    }

    public void popup_loc_enable(Activity act) {
        AlertDialog.Builder b = new AlertDialog.Builder(act);
        b.setTitle("You Must Enable Location To Use This App");
        b.setMessage("Press The OK Button To Activate Location");

        b.setCancelable(false);

        b.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.cancel();
                new FindLoc().execute();
            }
        });


        final AlertDialog alert = b.create();

        act.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                alert.show();
            }
        });
    }

    private class FindLoc extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... voids) {
            Log.e(TAG, "doInBackground: Checking permissions", null);
            getAllPermissions();
            getLocation();
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);

            Log.e(TAG, "onPostExecute: Done", null);
        }

        private void getLocation() {
            GoogleApiClient gac = new GoogleApiClient.Builder(getApplicationContext()).addApi(LocationServices.API).build();
            gac.connect();

            LocationRequest locationRequest = LocationRequest.create();
            locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
            locationRequest.setInterval(10000);
            locationRequest.setFastestInterval(10000 / 2);

            LocationSettingsRequest.Builder b = new LocationSettingsRequest.Builder().addLocationRequest(locationRequest);

            PendingResult<LocationSettingsResult> res = LocationServices.SettingsApi.checkLocationSettings(gac, b.build());
            res.setResultCallback(new ResultCallback<LocationSettingsResult>() {
                @Override
                public void onResult(@NonNull LocationSettingsResult locationSettingsResult) {
                    final com.google.android.gms.common.api.Status status = locationSettingsResult.getStatus();

                    switch (status.getStatusCode()) {
                        case LocationSettingsStatusCodes.SUCCESS:
                            Log.e(TAG, "onResult: Location is gucci", null);
                            perms.add(0);
                            break;
                        case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                            Log.e(TAG, "onResult: Location not gucci", null);

                            try {
                                startIntentSenderForResult(status.getResolution().getIntentSender(), 1, null, 0, 0, 0, null);
                            } catch (Exception e) {
                                Log.e(TAG, "onResult: Idk what this means: ", e);
                            }
                            break;
                    }
                }
            });
        }

        private void getAllPermissions() {

            if (!service.getBa().isEnabled()) {
                startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), 0);
            } else {
                perms.add(0);
            }

            if (ContextCompat.checkSelfPermission(SplashScreen.this.getApplicationContext(), Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "getAllPermissions: Getting Bluetooth Permission", null);
                ActivityCompat.requestPermissions(SplashScreen.this, new String[]{Manifest.permission.BLUETOOTH}, REQUEST_BLUETOOTH_CODE);
            }

            if (ContextCompat.checkSelfPermission(SplashScreen.this.getApplicationContext(), Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "getAllPermissions: Getting Bluetooth Admin Permission", null);
                ActivityCompat.requestPermissions(SplashScreen.this, new String[]{Manifest.permission.BLUETOOTH_ADMIN}, REQUEST_BLUETOOTH_ADMIN_CODE);
            }

            if (ContextCompat.checkSelfPermission(SplashScreen.this.getApplicationContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "getAllPermissions: Getting Coarse Loc Permission", null);
                ActivityCompat.requestPermissions(SplashScreen.this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_COARSE_LOCATION_CODE);
            }

            if (ContextCompat.checkSelfPermission(SplashScreen.this.getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "getAllPermissions: Getting Fine Loc Permission", null);
                ActivityCompat.requestPermissions(SplashScreen.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_FINE_LOCATION_CODE);
            }

            if (ContextCompat.checkSelfPermission(SplashScreen.this.getApplicationContext(), Manifest.permission.BLUETOOTH_PRIVILEGED) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "getAllPermissions: Getting Bluetooth Priv Permission", null);
                if (Build.VERSION.SDK_INT >= 19) {
                    ActivityCompat.requestPermissions(SplashScreen.this, new String[]{Manifest.permission.BLUETOOTH_PRIVILEGED}, REQUEST_BLUETOOTH_PRIV_CODE);
                }
            }
        }
    }

    private class WaitUntilPerms extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... voids) {
            int i = 0;

            Log.e(TAG, "run: Waiting...", null);

            while (perms.size() < 2) {
                i++;
            }

            Log.e(TAG, "doInBackground: had to wait for " + i + " frames.", null);

            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);

            handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {

                    if (Build.VERSION.SDK_INT >= 21) {
                        Log.e(TAG, "run: Moving to Main Activity with transition", null);
                        unbindService(serv_connection);
                        startActivity(new Intent(SplashScreen.this, MainActivity.class));
                        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                        finish();
                    } else {
                        Log.e(TAG, "run: Moving to Main Activity", null);
                        unbindService(serv_connection);
                        startActivity(new Intent(SplashScreen.this, MainActivity.class));
                        finish();
                    }
                }
            }, 3000);
        }
    }
}
