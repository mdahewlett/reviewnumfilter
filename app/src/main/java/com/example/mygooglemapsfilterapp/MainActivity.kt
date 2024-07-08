// how can I get this to be coloured code so it is easier to read
// can i used type safe etc on top of this?

package com.example.mygooglemapsfilterapp

// imports for adroid apps
import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log

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
import com.google.android.libraries.places.api.net.SearchByTextRequest
import com.google.android.libraries.places.api.model.RectangularBounds

import android.widget.Button

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var mFusedLocationProviderClient: FusedLocationProviderClient
    private val LOCATION_PERMISSION_REQUEST_CODE = 1

    private lateinit var searchBar: SearchView
    private lateinit var placesClient: PlacesClient
    private lateinit var reviewCountButton: Button
    private lateinit var lastQuery: String
    private lateinit var lastLatLng: LatLng

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // start places API
        val apiKey = BuildConfig.PLACES_API_KEY

        if (apiKey.isEmpty() || apiKey == "DEFAULT_API_KEY") {
            Log.e("places test", "No api key")
            finish()
            return
        }

        if (!Places.isInitialized()) {
            Places.initializeWithNewPlacesApiEnabled(applicationContext, apiKey)
        }

        placesClient = Places.createClient(this)

        // what does this do
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // what does this do
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        // what is this section
        searchBar = findViewById(R.id.search_bar)
        reviewCountButton = findViewById(R.id.review_count_button)
        searchBar.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) : Boolean {
                query?.let {
                    mFusedLocationProviderClient.lastLocation.addOnSuccessListener { location: Location? ->
                        location?.let {
                            lastLatLng = LatLng(it.latitude, it.longitude)
                            lastQuery = query
                            searchForLocation(query, lastLatLng, false)
                        }
                    }
                }
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                return false
            }
        })

        reviewCountButton.setOnClickListener {
            searchForLocation(lastQuery, lastLatLng, true)
        }
    }

    private fun searchForLocation(query: String, currentLatLng: LatLng, filterByReviews: Boolean) {

    val placeFields = listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG)

    val request = SearchByTextRequest.builder(query, placeFields)
        .setLocationRestriction(
            RectangularBounds.newInstance(
                LatLng(currentLatLng.latitude - 0.1, currentLatLng.longitude - 0.1),
                LatLng(currentLatLng.latitude + 0.1, currentLatLng.longitude + 0.1)
            )
        )
        .build();

    placesClient.searchByText(request)
        .addOnSuccessListener { response ->

        // Clear old map markers
        mMap.clear()

        val results = response.places
        val filteredResults = if (filterByReviews) {
            results.sortedByDescending { it.userRatingsTotal ?: 0 }.take(10)
        } else {
            results
        }

        for (place in filteredResults) {
            Log.i("MainActivity", place.id ?: "")
            Log.i("MainActivity", place.name ?: "")

            val latLng = place.latLng
            if (latLng != null) {

                // if place found, add marker, move camera to place
                mMap.addMarker(MarkerOptions().position(latLng).title(place.name))
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
            }
        }
    }.addOnFailureListener { exception ->
                Log.e("MainActivity", "Text search failed: ${exception.message}")
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
                // no way jose, send 'em to the Peg
                val winnipegLocation = LatLng(49.8951, -97.1384)
                mMap.addMarker(MarkerOptions().position(winnipegLocation).title("Somewhere over Winnipeg"))
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(winnipegLocation, 15f))
            }
        }
    }
}
