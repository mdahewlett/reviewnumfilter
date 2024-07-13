/* Comment legend
    "// Concise note of what the code does //"
    "What is going on, 
    either paraphrasing AND/OR an analogy"
 */

// Declare what package file belongs to //
    /* Like a team captain in dodgeball, 
       MainActivity.kt is picked by this package */
package com.example.mygooglemapsfilterapp

// Imports //
    /* The player receives their equipment, 
    like declarations, constructs, and short shorts */

// Adroid framework
import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.content.Context

// Android components
import android.widget.Button
import android.widget.SearchView
import android.widget.TextView

// Androidx libraries
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

import com.example.mygooglemapsfilterapp.databinding.ActivityMainBinding

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

// Define the class //
    // Every activity has 1 class that makes the object users see and interact with in the activity window
    /* What can we expect to see from MainActivity this season Tom?
       Well Brad, she inhereits traits from her mother AppCompatActivity
       so look out for similar moves, 
       and she's a map-style player (interface),
       so we can expect her to do something when the map object is ready */
class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    // Declarations //
      // Only available in the class, initialized later, done for readability or b/c required to declare and initialize separately
      // What moves has MainActivity been working on in practice that we'll see on court today Tom?
    // System
    private lateinit var binding: ActivityMainBinding

    // Map
    private lateinit var mMap: GoogleMap
    private lateinit var mFusedLocationProviderClient: FusedLocationProviderClient
    private val LOCATION_PERMISSION_REQUEST_CODE = 1
    private lateinit var mapFragment: SupportMapFragment

    // Search
    private lateinit var searchBar: SearchView
    private lateinit var placesClient: PlacesClient
    private lateinit var lastQuery: String
    private lateinit var lastLatLng: LatLng

    // Reviews
    private lateinit var reviewCountButton: Button
    private lateinit var highestReviewsTextView: TextView
    private lateinit var lowestReviewsTextView: TextView
    private lateinit var meanReviewsTextView: TextView

    // Setting up //
       // This player, like their parent, also does a warm up, but it's different
    override fun onCreate(savedInstanceState: Bundle?) {

        // Well actually its the same warmup start to finish, but they add more movements onto the end
        super.onCreate(savedInstanceState)

        // Look here for the css/html equivalent of how the app will look to the user
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Connect to Google Places platform //
           /* It looks like they're going to be getting some on court help
           from Google Places via a magic portal */

        // Start API
           // They're gathering the items they need for the incantation
        val apiKey = BuildConfig.PLACES_API_KEY

        if (apiKey.isEmpty() || apiKey == "DEFAULT_API_KEY") {
            Log.e("places test", "No api key")
            finish() // closes the activity
            return
        }

        // Start Places SDK
         // Yup they're trying to open the portal
        if (!Places.isInitialized()) {
            Places.initializeWithNewPlacesApiEnabled(applicationContext, apiKey)
        }

        // Makes object that my app uses to talk to Places API
          // They've successfully opened a portal
        placesClient = Places.createClient(this)

        // Initializing the map fragment from XML
           // Tells it that its a map fragment, since in xml it's a generic <fragment/>
           // They're preparing an item that is going to receive Google Places power
        mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        
        // Initializes the google map object inside the map fragment
         // They've attuned their item to Google Places
        mapFragment.getMapAsync(this)

        // Gets the device location
           // They're pulling other powers from Google Places and storing them
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        // Initializing more fragments
         // These fragment types are explicit in the xml <fragment name/>
         // Wow Brad, they're really summoning everything now
        searchBar = findViewById(R.id.search_bar)
        reviewCountButton = binding.reviewCountButton
        highestReviewsTextView = binding.highestReviews
        lowestReviewsTextView = binding.lowestReviews
        meanReviewsTextView = binding.meanReviews
        
        // SearchBar logic //
           // They're telling one of their key fragments what to moves to look for and how to respond

        // Pay attention to when the user interacts with the search bar
        searchBar.setOnQueryTextListener(object : SearchView.OnQueryTextListener {

            // If they hit submit
            override fun onQueryTextSubmit(query: String?) : Boolean {

                // And have put text into the search bar
                query?.let { 

                    // Look for results matching that text near their location, don't filter by review count
                    mFusedLocationProviderClient.lastLocation.addOnSuccessListener { location: Location? ->
                        location?.let {
                            lastLatLng = LatLng(it.latitude, it.longitude)
                            lastQuery = query

                            // Their first custom function Brad, we love to see it
                            searchForLocation(query, lastLatLng, false)
                        }
                    }
                }
                // Don't stop listening and responding
                return false
            }

            // If they change the query text
            override fun onQueryTextChange(newText: String?): Boolean {
                
                // Clear markers when they search bar is empty
                if (newText.isNullOrEmpty()) {                    
                    // mMap.clear()
                }

                return false // But don't stop listening and responding
            }
        })

        // Clear markers and hide keyboard when the search bar is closed
        searchBar.setOnCloseListener {
            clearSearchView()
            true // Return false to let the default behavior proceed
        }

        // If the review count button is clicked,
        reviewCountButton.setOnClickListener {
            
            // then filter by review count
            searchForLocation(lastQuery, lastLatLng, true)
        }
    }

    // Their location permission //
        // Callback when user allows or denies location
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {

        // Does the normal stuff when permission results are in
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        // An app can request many things, checks user response is RE location permission
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {

            // If they say yes
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Run the map
                onMapReady(mMap)

            // If its not a hell yes
            } else {
                // send 'em to the Peg
                val winnipegLocation = LatLng(49.8951, -97.1384)
                mMap.addMarker(MarkerOptions().position(winnipegLocation).title("Somewhere over Winnipeg"))
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(winnipegLocation, 15f))
            }
        }
    }

    // Load the base map //
       // Fulfills map interface contract
       // Brad here's we're going to see why they're truely a map-style player
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        // Enable zoom controls
        mMap.uiSettings.isZoomControlsEnabled = true

        // If missing existing location permission,
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
        
                // then ask for it
                ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 
                    LOCATION_PERMISSION_REQUEST_CODE)
                
                // Don't do more without permission
                return

                // Its a callback function, so more code will execute from the onRequestPermission block
                // once the user says yay/nay
            }

        // Show dot of their location on map (considered its own layer)
        mMap.isMyLocationEnabled = true

        // Send the camera to where they were last //

        // Request the last location
        mFusedLocationProviderClient.lastLocation

            // If there's a response
            .addOnSuccessListener { location: Location? -> 
            
            // With a non null location
            location?.let {

                // Convert it to lat and long
                val currentLocation = LatLng(it.latitude, it.longitude)
               
                // And move the camera to the new location
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 15f))
            }
        }
    }

    private fun clearSearchView() {
        // Clear text
        searchBar.setQuery("", false)
        searchBar.clearFocus()

        // Clear markers
        mMap.clear()

        // hide keyboard
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(searchBar.windowToken, 0)

        // change focus to map
        mapFragment.view?.requestFocus()

    }

    // Add markers for queries //
       // Lets take moment before the action to see a key play this player's been cooking up
    private fun searchForLocation(query: String, currentLatLng: LatLng, filterByReviews: Boolean) {
        
        // Get the places that match the query //

        // State the info we want on each place
        val placeFields = listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG, Place.Field.USER_RATINGS_TOTAL)

        // Put our request for places and the place infos in the right format for the API
        val request = SearchByTextRequest.builder(query, placeFields)
            .setLocationRestriction(
                RectangularBounds.newInstance(
                    LatLng(currentLatLng.latitude - 0.1, currentLatLng.longitude - 0.1),
                    LatLng(currentLatLng.latitude + 0.1, currentLatLng.longitude + 0.1)
                )
            )
            .build();

        // Send the request to the API
        placesClient.searchByText(request)

            // If the API sends the info back
            .addOnSuccessListener { response ->

                // Clear old map markers
                mMap.clear()

                // Put the places in a variable
                val results = response.places

                // If we're filtering,
                val filteredResults = if (filterByReviews) {

                    // take the top 10 most reviewed spots,
                    results.sortedByDescending { it.userRatingsTotal ?: 0 }.take(10)
               
                // otherwise give all the results
                } else {
                    results
                }

                // Fill in the location summary stats //

                // Make a list of just the number of reviews
                val reviewList = results.mapNotNull { it.userRatingsTotal }

                if (reviewList.isNotEmpty()) {

                    // Save the highest, lowest, and mean to their own var
                    val highestReviews = reviewList.maxOrNull() ?: 0
                    val lowestReviews = reviewList.minOrNull() ?: 0
                    val meanReviews = reviewList.average().toInt()

                    // Display those values to the user in the respective fragments
                    highestReviewsTextView.text = "Highest Reviews: $highestReviews"
                    lowestReviewsTextView.text = "Lowest Reviews: $lowestReviews"
                    meanReviewsTextView.text = "Mean Reviews: $meanReviews"
                }


                // Add markers to the map //

                for (place in filteredResults) {

                    // Log for development purposes, removed in production
                    Log.i("MainActivity", place.id ?: "")
                    Log.i("MainActivity", place.name ?: "")

                    val latLng = place.latLng
                    if (latLng != null) {
                        val reviews = place.userRatingsTotal ?: 0
                        val snippet = "Reviews: $reviews"

                        // If place found, add marker with info on the place,
                        mMap.addMarker(MarkerOptions().position(latLng).title(place.name).snippet(snippet))
                        
                        // and move the camera to the place
                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                    }
                }

            // If the API doesn't send the info back
            }.addOnFailureListener { exception ->

                // Log the error
                Log.e("MainActivity", "Text search failed: ${exception.message}")
            }
    }

}
