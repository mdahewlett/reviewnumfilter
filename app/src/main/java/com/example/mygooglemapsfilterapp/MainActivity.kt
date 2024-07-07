package com.example.mygooglemapsfilterapp

// imports for adroid apps
import android.Manifest
import android.content.pm.PackageManager
import android.location.Location

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

// imports for user location
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices

// imports for the basic map
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions

// imports for searching for things
import android.widget.SearchView

import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.net.FindCurrentPlaceRequest
import com.google.android.libraries.places.api.net.PlacesClient

import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.net.PlacesStatusCodes


// imports for error handling
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.tasks.Task
import android.util.Log
import android.widget.Toast

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var mFusedLocationProviderClient: FusedLocationProviderClient
    private val LOCATION_PERMISSION_REQUEST_CODE = 1

    private lateinit var searchBar: SearchView
    private lateinit var placesClient: PlacesClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // start places API
        if (!Places.isInitialized()) {
            Places.initialize(applicationContext, getString(R.string.google_maps_key))
        }
        placesClient = Places.createClient(this)

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        val searchView = findViewById<SearchView>(R.id.search_bar)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) : Boolean {
                query?.let {
                    searchForLocation(it)
                }
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                return false
            }
        })
    }

    private fun searchForLocation(query: String) {
        val request = FindCurrentPlaceRequest.newInstance(listOf(Place.Field.NAME, Place.Field.LAT_LNG))
        val placeResult = placesClient.findCurrentPlace(request)
        placeResult.addOnCompleteListener { task ->
        if (task.isSuccessful) {
            val response = task.result
            for (placeLikelihood in response?.placeLikelihoods ?: emptyList()) {
                val place = placeLikelihood.place
                // Give each place a marker
                mMap.addMarker(MarkerOptions().position(place.latLng!!).title(place.name))
            }
            // Camera goes to first place
            response?.placeLikelihoods?.firstOrNull()?.place?.latLng?.let {
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(it, 15f))
            }
        } else {
            // Handle error
            Log.e("MainActivity", "Place not found: 4{task.exception?.message}")
            when (task.exception?.message) {
                "API key is invalid" -> Log.e("MainActivity", "Invalid API Key")
                "API quota exceeded" -> Log.e("MainActivity", "API Quota Exceeded")
                else -> Log.e("MainActivity", "Unknown error occured")
            }
        }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        // Check for location permission
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // Ask for permission
                ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 
                    LOCATION_PERMISSION_REQUEST_CODE)
                return
            }

        // enable the map layer called my location
        mMap.isMyLocationEnabled = true

        // where were they last, send the camera there
        mFusedLocationProviderClient.lastLocation
            .addOnSuccessListener { location: Location? -> 
            // got location, apparently can be null sometimes, but not common
            location?.let {
                val currentLocation = LatLng(it.latitude, it.longitude)
                mMap.addMarker(MarkerOptions().position(currentLocation).title("You are here"))
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 15f))
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // all good to go
                onMapReady(mMap)
            } else {
                // not way jose, send 'em to the Peg
                val winnipegLocation = LatLng(49.8951, -97.1384)
                mMap.addMarker(MarkerOptions().position(winnipegLocation).title("Somewhere over Winnipeg"))
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(winnipegLocation, 15f))
            }
        }
    }
}
