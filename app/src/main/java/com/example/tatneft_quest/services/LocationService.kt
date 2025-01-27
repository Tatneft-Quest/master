package com.example.tatneft_quest.services

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.*
import android.util.Log
import androidx.core.app.ActivityCompat
import com.example.tatneft_quest.MainActivity
import com.example.tatneft_quest.R
import com.example.tatneft_quest.Variables.Companion.LATITUDE
import com.example.tatneft_quest.Variables.Companion.LONGITUDE
import com.example.tatneft_quest.Variables.Companion.TIME
import com.example.tatneft_quest.Variables.Companion.TIME_QUEST
import com.example.tatneft_quest.baseClasses.MyApplication
import com.example.tatneft_quest.libs.ImprovedPreference
import com.example.tatneft_quest.utils.AlertDialogGpsActivity
import com.google.android.gms.location.*
import java.util.*


@Suppress("DEPRECATION")
class LocationService : Service() {
    private var mFusedLocationClient: FusedLocationProviderClient? = null
    private var notificationManager: NotificationManager? = null
    private var notification: Notification? = null
    private var mLocationCallback: LocationCallback? = null
    private var mHandler: Handler? = Handler()
    private var mRunnable: Runnable? = null
    private lateinit var mLocationRequestHighAccuracy: LocationRequest

    private var improvedPreference: ImprovedPreference? = null
    private val timerHandler = Handler()
    private var startTime = 0L
    private var saveTime = 0L
    private var timeInMilliseconds = 0L
    private var updatedTime = 0L

    companion object {
        private const val UPDATE_INTERVAL: Long = 4000
        private const val FASTEST_INTERVAL: Long = 2000
        private const val ACTION_STOP_SERVICE = "stop"
        private const val ACTION_START_ACTIVITY = "start"
        private const val TAG = "check"
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        improvedPreference = ImprovedPreference(this)
        sendNotification()
        checkLocation()
        checkGPS()
        timer()
        requestLocationUpdates()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (ACTION_STOP_SERVICE == intent?.action) {
            if (!(applicationContext as MyApplication).isAppForeground()) {
                removeLocationUpdates()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        removeLocationUpdates()
        super.onDestroy()
    }

    private fun sendNotification() {
        val stopIntent = Intent(this, LocationService::class.java)
        stopIntent.action = ACTION_STOP_SERVICE
        val pendingStopSelf =
            PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT)

        val startIntent = Intent(this, MainActivity::class.java)
        startIntent.action = ACTION_START_ACTIVITY
        startIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK
        val pendingStart = PendingIntent.getActivity(this, 0, startIntent, startIntent.flags)

        if (Build.VERSION.SDK_INT >= 26) {
            val channelId = "my_channel_01"
            val channel = NotificationChannel(channelId,
                "My Channel",
                NotificationManager.IMPORTANCE_DEFAULT)

            notificationManager?.createNotificationChannel(channel)
            notification = Notification.Builder(this, channelId)
                .setAutoCancel(false)
                .setTicker("this is ticker text")
                .setContentTitle("Tatneft Quest")
                .setContentText("Считываем вашу локацию")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingStart)
                .setOngoing(false)
                .addAction(R.drawable.ic_logout, "Закрыть", pendingStopSelf)
                .build()
        } else {
            notification = Notification.Builder(this)
                .setAutoCancel(false)
                .setTicker("this is ticker text")
                .setContentTitle("Tatneft Quest")
                .setContentText("Считываем вашу локацию")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingStart)
                .setOngoing(false)
                .addAction(R.drawable.ic_logout, "Закрыть", pendingStopSelf)
                .build()
        }

        startForeground(1, notification)
    }

    private fun checkLocation() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "getLocation: stopping the location service.")
            stopSelf()
            return
        }

        mLocationRequestHighAccuracy = LocationRequest()
        mLocationRequestHighAccuracy.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        mLocationRequestHighAccuracy.interval = UPDATE_INTERVAL
        mLocationRequestHighAccuracy.fastestInterval = FASTEST_INTERVAL

        mLocationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location: Location? = locationResult.lastLocation
                if (location != null) {
                    LATITUDE = location.latitude
                    LONGITUDE = location.longitude
                    Log.d(TAG, "onLocationResult")
                }
            }
        }
    }

    private fun checkGPS() {
        mHandler?.postDelayed(Runnable {
            Log.d(TAG, "checkGPS")
            val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                startActivity(Intent(applicationContext, AlertDialogGpsActivity::class.java)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                removeLocationUpdates()
                return@Runnable
            }
            mHandler?.postDelayed(mRunnable!!, UPDATE_INTERVAL)
        }.also { mRunnable = it }, UPDATE_INTERVAL)
    }

    private fun timer() {
        startTime = SystemClock.uptimeMillis()
        TIME = if (improvedPreference?.getInt((TIME_QUEST)) != 0) {
            improvedPreference?.getInt(TIME_QUEST)!!
        } else {
            0
        }
        saveTime = TIME.toLong()
        timerHandler.postDelayed(updateTimerThread, 0)
    }

    private val updateTimerThread: Runnable = object : Runnable {
        override fun run() {
            timeInMilliseconds = SystemClock.uptimeMillis() - startTime
            updatedTime = saveTime + timeInMilliseconds
            var sec = (updatedTime / 1000).toInt()
            var min = sec / 60
            var hour = min / 60
            sec %= 60; min %= 60; hour %= 60
            TIME = (hour * 3600 * 1000) + (min * 60 * 1000) + (sec * 1000)
            improvedPreference?.putInt(TIME_QUEST, TIME)
            timerHandler.postDelayed(this, 1000)
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationUpdates() {
        mFusedLocationClient?.requestLocationUpdates(mLocationRequestHighAccuracy,
            mLocationCallback, Looper.myLooper())
    }

    private fun stopTimer() {
        improvedPreference?.putInt(TIME_QUEST, TIME)
        timerHandler.removeCallbacks(updateTimerThread)
    }

    private fun removeLocationUpdates() {
        Log.d(TAG, "removeLocationUpdates")
        stopTimer()
        mFusedLocationClient?.removeLocationUpdates(mLocationCallback)
        mHandler?.removeCallbacks(mRunnable!!)
        stopSelf()
    }

}