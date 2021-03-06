/*
 * This file is part of Salsa Beacons
 *
 * Salsa Beacons is a Bluetooth LE aware Android app that enables location dependant learning
 * author:  Richard Greenwood <richard.greenwood@open.ac.uk>
 * Copyright (C) 2015 The Open University
 *
 * Salsa Beacons is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * any later version.
 *
 * Salsa Beacons is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with Salsa Beacons.  If not, see <http://www.gnu.org/licenses/>.
 */

package uk.ac.open.salsabeacons;

import android.app.Application;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.app.Notification;
import android.graphics.Typeface;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.Vibrator;
import android.util.Log;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.Identifier;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;
import org.altbeacon.beacon.powersave.BackgroundPowerSaver;
import org.altbeacon.beacon.startup.RegionBootstrap;
import org.altbeacon.beacon.startup.BootstrapNotifier;

import java.util.Collection;

/**
 * Created by rmg29 on 22/01/2015.
 */
public class BeaconReferenceApplication extends Application implements BootstrapNotifier, RangeNotifier {
  private static final String TAG = "BeaconReferenceApp";
  private static final String SALSABEACONSID = "46A88354-06AC-4743-A762-901C0717596E";
  private static Context mContext;
  //public final static String SALSA_BEACON_ID = "uk.ac.open.salsabeacons.SALSA_BEACON_ID";
  private Typeface fontAwesome;
  private RegionBootstrap regionBootstrap;
  private BackgroundPowerSaver backgroundPowerSaver;
  private BeaconManager beaconManager = null;
  private LifecycleHandler mLifecycleHandler = null;

  public void onCreate() {
    super.onCreate();
    mContext = this;
    fontAwesome = Typeface.createFromAsset(getAssets(), "font-awesome-4.3.0/fonts/fontawesome-webfont.ttf");
    mLifecycleHandler = new LifecycleHandler();
    registerActivityLifecycleCallbacks(mLifecycleHandler);
    beaconManager = org.altbeacon.beacon.BeaconManager.getInstanceForApplication(this);
    beaconManager.setForegroundScanPeriod(2000);
    beaconManager.setForegroundBetweenScanPeriod(300);
    beaconManager.setBackgroundScanPeriod(2400);
    beaconManager.setBackgroundBetweenScanPeriod(5000);
    // By default the AndroidBeaconLibrary will only find AltBeacons.  If you wish to make it
    // find a different type of beacon, you must specify the byte layout for that beacon's
    // advertisement with a line like below.  The example shows how to find a beacon with the
    // same byte layout as AltBeacon but with a beaconTypeCode of 0xaabb.  To find the proper
    // layout expression for other beacon types, do a web search for "setBeaconLayout"
    // including the quotes.
    //
    // beaconManager.getBeaconParsers().add(new BeaconParser().
    //        setBeaconLayout("m:2-3=aabb,i:4-19,i:20-21,i:22-23,p:24-24,d:25-25"));
    //

    beaconManager.getBeaconParsers().add(new BeaconParser().
        setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24"));

    Log.d(TAG, "setting up background monitoring for beacons and power saving");
    // wake up the app when a beacon is seen

    Region region = new Region("salsaRegion", Identifier.parse(this.SALSABEACONSID), null, null);
    regionBootstrap = new RegionBootstrap(this, region);
    BeaconManager.setDebug(true);

    // simply constructing this class and holding a reference to it in your custom Application
    // class will automatically cause the BeaconLibrary to save battery whenever the application
    // is not visible.  This reduces bluetooth power usage by about 60%
    backgroundPowerSaver = new BackgroundPowerSaver(this);
  }

  public Typeface getIconFont() {
    return fontAwesome;
  }

  public static Context getContext(){
    return mContext;
  }

  @Override
  public void didEnterRegion(Region region) {
    Log.d(TAG, "ENTERED a Region: " + region);
    beaconManager.setRangeNotifier(this);

    try {
      beaconManager.startRangingBeaconsInRegion(region);
    }
    catch (RemoteException e) {

    }
  }

  @Override
  public void didRangeBeaconsInRegion(Collection<Beacon> beacons, Region region) {
    Log.d(TAG, "Amount of Beacons in region on this scan: " + beacons.size());
    if(beacons.size() > 0) {
      for (Beacon beacon : beacons) {
        SalsaBeacon.handleFoundBeacon(beacon.getId2().toInt(), beacon.getId3().toInt(), beacon.getDistance());
      }
    }

  }

  @Override
  public void didExitRegion(Region region) {
    Log.d(TAG, "EXITED a Region");
    /*if (monitoringActivity != null) {
      monitoringActivity.logToDisplay("I no longer see a beacon.");
    }*/
  }

  @Override
  public void didDetermineStateForRegion(int state, Region region) {
    Log.d(TAG, "did Determine State For Region");
    /*if (monitoringActivity != null) {
      monitoringActivity.logToDisplay("I have just switched from seeing/not seeing beacons: " + state);
    }*/
  }

  public void sendNotification() {

    PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
    if(!mLifecycleHandler.isApplicationVisible() || !pm.isScreenOn()) {
      Notification.Builder builder =
          new Notification.Builder(this)
              .setContentTitle(getResources().getString(R.string.app_name))
              .setContentText(getResources().getString(R.string.notification))
              .setSmallIcon(R.drawable.ic_white_salsa)
              .setAutoCancel(true);

      TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
      Intent intent = new Intent(this, MonitoringActivity.class);
      stackBuilder.addNextIntent(intent);
      PendingIntent resultPendingIntent =
          stackBuilder.getPendingIntent(
              0,
              PendingIntent.FLAG_UPDATE_CURRENT
          );
      builder.setContentIntent(resultPendingIntent);
      NotificationManager notificationManager =
          (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
      notificationManager.notify(1, builder.build());
      Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
      v.vibrate(500); // Vibrate for 500 milliseconds
    }
  }

  public void cancelNotification() {
    NotificationManager notificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
    notificationManager.cancel(1);
  }
}