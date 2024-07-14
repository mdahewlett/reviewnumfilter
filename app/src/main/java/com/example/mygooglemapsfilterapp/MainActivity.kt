package com.example.mygooglemapsfilterapp

// Adroid framework
import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.view.Menu
import android.view.MenuItem


import android.content.Context

// Android components
import android.widget.RelativeLayout
import android.widget.LinearLayout
import android.widget.Button
import android.widget.SearchView
import android.widget.TextView

// Androidx libraries
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

// Google Location Services
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices

// Google Maps
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions

// Places API
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.RectangularBounds
import com.google.android.libraries.places.api.net.FindCurrentPlaceRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.api.net.SearchByTextRequest

// Misc
import com.example.mygooglemapsfilterapp.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    // System
    private lateinit var binding: ActivityMainBinding

    // Map
    private lateinit var mMap: GoogleMap
    private lateinit var mFusedLocationProviderClient: FusedLocationProviderClient
    private val LOCATION_PERMISSION_REQUEST_CODE = 1
    private lateinit var mapFragment: SupportMapFragment

    // Search
    private lateinit var searchView: SearchView
    private lateinit var placesClient: PlacesClient
    private lateinit var lastQuery: String
    private lateinit var lastLatLng: LatLng

    // Reviews
    private lateinit var reviewCountButton: Button
    private lateinit var reviewCountSummary: LinearLayout
    private lateinit var highestReviewsTextView: TextView
    private lateinit var meanReviewsTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        // Typesafety
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Connect to Google Places platform
        val apiKey = BuildConfig.PLACES_API_KEY

        if (apiKey.isEmpty() || apiKey == "DEFAULT_API_KEY") {
            Log.e("places test", "No api key")
            finish() // closes the activity
            return
        }

        if (!Places.isInitialized()) {
            Places.initializeWithNewPlacesApiEnabled(applicationContext, apiKey)
        }

        placesClient = Places.createClient(this)

        // Get device location
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        
        // Initialize var
        mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        reviewCountButton = binding.reviewCountButton
        reviewCountSummary = binding.reviewCountSummary
        highestReviewsTextView = binding.highestReviews
        meanReviewsTextView = binding.meanReviews
        
        reviewCountSummary.visibility = View.GONE

        // Review filter logic
        reviewCountButton.setOnClickListener {
            searchForLocation(lastQuery, lastLatLng, true)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        val searchItem = menu?.findItem(R.id.action_search)

        if (searchItem == null) {
        Log.e("MainActivity", "Search item is null")
        return true
        }

        val searchView = searchItem.actionView as? SearchView

        if (searchView == null) {
            Log.e("MainActivity", "SearchView is null")
            return true
        }

        Log.d("MainActivity", "SearchView initialized")

        // Search view logic
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                Log.d("MainActivity", "Query submitted: $query")
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
                if (newText.isNullOrEmpty()) {     
                    Log.d("MainActivity", "Query text cleared")               
                    mMap.clear()
                    reviewCountSummary.visibility = View.GONE
                }

                return false
            }
        })
        return true
    }

    // Request location permission
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                onMapReady(mMap)
            } else {
                val winnipegLocation = LatLng(49.8951, -97.1384)
                mMap.addMarker(MarkerOptions().position(winnipegLocation).title("Somewhere over Winnipeg"))
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(winnipegLocation, 15f))
            }
        }
    }

    // Load base map
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        // Enable zoom controls
        mMap.uiSettings.isZoomControlsEnabled = true

        // If missing existing location permission
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
        
                // Request permission
                ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 
                    LOCATION_PERMISSION_REQUEST_CODE)
                
                // Don't do more without permission
                return

            }

        // Show dot of their location on map
        mMap.isMyLocationEnabled = true

        // Request last location, move camera there
        mFusedLocationProviderClient.lastLocation
            .addOnSuccessListener { location: Location? -> 
            location?.let {
                val currentLocation = LatLng(it.latitude, it.longitude)
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 15f))
            }
        }

        // Move location button to bottom right
        val locationButtonParent = (mapFragment.view?.findViewById<View>("1".toInt())?.parent as View)
        
        locationButtonParent.background = ContextCompat.getDrawable(this, R.drawable.border) // debug

        val locationButton = locationButtonParent.findViewById<View>("2".toInt())
        val rlp = locationButton.layoutParams as RelativeLayout.LayoutParams
        rlp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE)
        rlp.addRule(RelativeLayout.ALIGN_PARENT_END, RelativeLayout.TRUE)
        rlp.setMargins(0, 0, 16, 100) // Adjust margins as needed
        locationButton.layoutParams = rlp
    }

    // Search query logic
    private fun searchForLocation(query: String, currentLatLng: LatLng, filterByReviews: Boolean) {
        // State info we want on each place
        val placeFields = listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG, Place.Field.USER_RATINGS_TOTAL)

        // Format request for the API
        val request = SearchByTextRequest.builder(query, placeFields)
            .setLocationRestriction(
                RectangularBounds.newInstance(
                    LatLng(currentLatLng.latitude - 0.1, currentLatLng.longitude - 0.1),
                    LatLng(currentLatLng.latitude + 0.1, currentLatLng.longitude + 0.1)
                )
            )
            .build();

        // Send request
        placesClient.searchByText(request)
            .addOnSuccessListener { response ->
                mMap.clear()
                val results = response.places

                // Optional filter by review number
                val filteredResults = if (filterByReviews) {
                    results.sortedByDescending { it.userRatingsTotal ?: 0 }.take(10)
                } else {
                    results
                }

                // Display review summary stats
                val reviewList = results.mapNotNull { it.userRatingsTotal }

                if (reviewList.isNotEmpty()) {
                    val highestReviews = reviewList.maxOrNull() ?: 0
                    val meanReviews = reviewList.average().toInt()

                    highestReviewsTextView.text = "Highest Reviews: $highestReviews"
                    meanReviewsTextView.text = "Mean Reviews: $meanReviews"
                
                    reviewCountSummary.visibility = View.VISIBLE
                }

                // Add map markers
                for (place in filteredResults) {

                    // Log for development purposes, removed in production
                    Log.i("MainActivity", place.id ?: "")
                    Log.i("MainActivity", place.name ?: "")

                    val latLng = place.latLng
                    if (latLng != null) {
                        val reviews = place.userRatingsTotal ?: 0
                        val snippet = "Reviews: $reviews"

                        mMap.addMarker(MarkerOptions().position(latLng).title(place.name).snippet(snippet))
                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                    }
                }

            }.addOnFailureListener { exception ->
                Log.e("MainActivity", "Text search failed: ${exception.message}")
            }
    }

}
