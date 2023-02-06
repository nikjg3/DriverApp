package com.example.driverapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.ActivityCompat
import com.amplifyframework.AmplifyException
import com.amplifyframework.api.aws.AWSApiPlugin
import com.amplifyframework.core.Amplify
import com.amplifyframework.core.model.query.Where
import com.amplifyframework.datastore.AWSDataStorePlugin
import com.amplifyframework.datastore.generated.model.Location
import com.example.driverapp.utils.LocationPermissionHelper
import com.mapbox.android.gestures.MoveGestureDetector
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.expressions.dsl.generated.interpolate
import com.mapbox.maps.plugin.LocationPuck2D
import com.mapbox.maps.plugin.gestures.OnMoveListener
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.locationcomponent.OnIndicatorBearingChangedListener
import com.mapbox.maps.plugin.locationcomponent.OnIndicatorPositionChangedListener
import com.mapbox.maps.plugin.locationcomponent.location
import java.lang.ref.WeakReference
import java.util.*


/**
 * Tracks the user location on screen, simulates a navigation session.
 */
class Main : AppCompatActivity() {


    val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var locationPermissionHelper: LocationPermissionHelper

    private val onIndicatorBearingChangedListener = OnIndicatorBearingChangedListener {
        mapView.getMapboxMap().setCamera(CameraOptions.Builder().bearing(it).build())
    }

    private val onIndicatorPositionChangedListener = OnIndicatorPositionChangedListener {
        mapView.getMapboxMap().setCamera(CameraOptions.Builder().center(it).build())
        mapView.gestures.focalPoint = mapView.getMapboxMap().pixelForCoordinate(it)
    }

    private val onMoveListener = object : OnMoveListener {
        override fun onMoveBegin(detector: MoveGestureDetector) {
            onCameraTrackingDismissed()
        }

        override fun onMove(detector: MoveGestureDetector): Boolean {
            return false
        }

        override fun onMoveEnd(detector: MoveGestureDetector) {}
    }
    private lateinit var mapView: MapView

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mapView = findViewById(R.id.mapView)
        tickHandler();
        //Amplify Init
        try {
            Amplify.addPlugin(AWSApiPlugin())
            Amplify.addPlugin(AWSDataStorePlugin())
            Amplify.configure(applicationContext)
            Log.i("MyAmplifyApp", "Initialized Amplify")
        } catch (error: AmplifyException) {
            Log.e("MyAmplifyApp", "Could not initialize Amplify", error)
        }
        //End

        locationPermissionHelper = LocationPermissionHelper(WeakReference(this))
        locationPermissionHelper.checkPermissions {
            onMapReady()
        }

        //Build amplify Item
        val item = Location.builder()
            .longitude(0.0)
            .latitude(0.0)
            .build()
        //Create Item
        Amplify.DataStore.save(item,
            { Log.i("Tutorial", "Created coordinates: ${item.longitude} ${item.latitude}") },
            { Log.e("Tutorial", "Could not save item to DataStore", it) }
        )

    }

    fun updateLocation() {
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        val locationProvider = LocationManager.GPS_PROVIDER
        val location = locationManager.getLastKnownLocation(locationProvider)
        if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return
            }
        if (location != null) {
            val latitude = location.latitude
            val longitude = location.longitude
            val longText = findViewById<View>(R.id.longitude) as TextView
            val latText = findViewById<View>(R.id.latitude) as TextView
            longText.text = "Longitude: " + longitude.toBigDecimal().toPlainString();
            latText.text = "Latitude: " + latitude.toBigDecimal().toPlainString();
            Log.i("MyApp", "Latitude: $latitude, Longitude: $longitude")


            Amplify.DataStore.query(Location::class.java, Location.LONGITUDE.contains(""),
                { matches  ->
                    if (matches.hasNext()) {
                        val location = matches.next()
                        val updatedLocation = location.copyOfBuilder()
                            .longitude(longitude)
                            .latitude(latitude)
                            .build()
                        Amplify.DataStore.save(updatedLocation,
                            { Log.i("Tutorial", "Updated longitude: ${updatedLocation.longitude} Updated latitude: ${updatedLocation.latitude}") },
                            { Log.e("Tutorial", "Update failed.", it) }
                        )
                    }
                },
                { Log.e("Tutorial", "Query failed", it) }
            )
            //Delete Item
                /*Amplify.DataStore.query(Location::class.java,
                    Where.matches(Location.LONGITUDE.contains("1.0")),
                    { matches ->
                        if (matches.hasNext()) {
                            val toDeleteTodo = matches.next()
                            Amplify.DataStore.delete(toDeleteTodo,
                                { Log.i("Tutorial", "Deleted item: ${toDeleteTodo.id}") },
                                { Log.e("Tutorial", "Delete failed.", it) }
                            )
                        }
                    },
                    { Log.e("Tutorial", "Query failed.", it) }
                )
        } else {
            Log.i("MyApp", "Location not available")*/
        }

    }
    fun tickHandler() {
        mainHandler.post(object : Runnable {
            override fun run() {
                Log.i("MyApp", "Ticking")
                updateLocation();
                mainHandler.postDelayed(this, 1000)
            }
        })
    }
    private fun onMapReady() {
        mapView.getMapboxMap().setCamera(
            CameraOptions.Builder()
                .zoom(14.0)
                .build()
        )
        mapView.getMapboxMap().loadStyleUri(
            Style.MAPBOX_STREETS
        ) {
            initLocationComponent()
            setupGesturesListener()
        }
    }

    private fun setupGesturesListener() {
        mapView.gestures.addOnMoveListener(onMoveListener)
    }


    private fun initLocationComponent() {
        val locationComponentPlugin = mapView.location
        locationComponentPlugin.updateSettings {
            this.enabled = true
            this.locationPuck = LocationPuck2D(
                bearingImage = AppCompatResources.getDrawable(
                    this@Main,
                    R.drawable.driver_puck,
                ),
                shadowImage = AppCompatResources.getDrawable(
                    this@Main,
                    R.drawable.driver_puck,
                ),
                scaleExpression = interpolate {
                    linear()
                    zoom()
                    stop {
                        literal(0.0)
                        literal(0.6)
                    }
                    stop {
                        literal(20.0)
                        literal(1.0)
                    }
                }.toJson()
            )
        }
        locationComponentPlugin.addOnIndicatorPositionChangedListener(
            onIndicatorPositionChangedListener
        )
        locationComponentPlugin.addOnIndicatorBearingChangedListener(
            onIndicatorBearingChangedListener
        )
    }

    private fun onCameraTrackingDismissed() {
        Toast.makeText(this, "onCameraTrackingDismissed", Toast.LENGTH_SHORT).show()
        mapView.location
            .removeOnIndicatorPositionChangedListener(onIndicatorPositionChangedListener)
        mapView.location
            .removeOnIndicatorBearingChangedListener(onIndicatorBearingChangedListener)
        mapView.gestures.removeOnMoveListener(onMoveListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.location
            .removeOnIndicatorBearingChangedListener(onIndicatorBearingChangedListener)
        mapView.location
            .removeOnIndicatorPositionChangedListener(onIndicatorPositionChangedListener)
        mapView.gestures.removeOnMoveListener(onMoveListener)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        locationPermissionHelper.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
}

