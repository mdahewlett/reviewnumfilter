package com.example.mygooglemapsfilterapp

// Adroid framework
import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.LayoutInflater
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.util.TypedValue
import android.view.MotionEvent
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.view.Gravity

// Android components
import android.widget.RelativeLayout
import android.widget.LinearLayout
import android.widget.Button
import android.widget.SearchView
import android.widget.TextView
import android.widget.ImageButton
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import android.widget.ScrollView
import com.google.android.material.slider.RangeSlider
import android.widget.RadioGroup
import android.widget.RadioButton
import android.widget.GridLayout

// Androidx libraries
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AlertDialog
import androidx.annotation.DrawableRes
import androidx.annotation.ColorRes

// Google Location Services
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices

// Google Maps
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory

// Places API
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Callback
import okhttp3.Call
import okhttp3.Response
import java.io.IOException
import org.json.JSONObject
import java.net.URLEncoder
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.RectangularBounds
import com.google.android.libraries.places.api.net.FindCurrentPlaceRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.api.net.SearchByTextRequest

// Custom marker
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint

// Misc
import com.example.mygooglemapsfilterapp.databinding.ActivityMainBinding
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.GradientDrawable

// Filter
import kotlin.math.ceil
import kotlin.math.floor
import org.apache.commons.math3.ml.clustering.Cluster
import org.apache.commons.math3.ml.clustering.Clusterable
import org.apache.commons.math3.ml.clustering.KMeansPlusPlusClusterer
import org.apache.commons.math3.ml.clustering.DoublePoint
import android.view.ViewGroup

data class ClusterRange(val label: String, val min: Int, val max: Int)

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    // System
    private lateinit var binding: ActivityMainBinding

    // Map
    private lateinit var mMap: GoogleMap
    private lateinit var mFusedLocationProviderClient: FusedLocationProviderClient
    private val LOCATION_PERMISSION_REQUEST_CODE = 1
    private lateinit var mapFragment: SupportMapFragment
    private lateinit var myLocationButton: ImageButton
    private var isCameraCentered = false
    private val markerPlaceMap = mutableMapOf<Marker, PlaceData>()
    private var selectedMarker: Marker? = null

    // Search
    private lateinit var searchView: SearchView
    private lateinit var placesClient: PlacesClient
    private lateinit var lastQuery: String
    private lateinit var lastLatLng: LatLng
    private var noResultsDialog: AlertDialog? = null

    // Loading
    private lateinit var loadingStateMessage: LinearLayout
    private lateinit var loadingProgressText: TextView
    private lateinit var handler: Handler
    private lateinit var progressRunnable: Runnable
    private var loadingState = 0

    // Reviews
    private lateinit var reviewCountButton: Button
    private lateinit var superReviewButton: Button
    private lateinit var clusters: Map<Int, String>
    private var noSuper = false
    private var roundedHighestReviews: Float = 0f 

    // Filter
    private var previousMin: Float = 0f
    private var previousMax: Float = 0f
    private var rangeSliderFrom: Float = 0f
    private var rangeSliderTo: Float = 0f
    private var sliderValueWidth: Int = 0
    private var sliderValuePositionX: Float? = null
    private var selectedCategory: String = "All"
    private var isDoneButtonClicked = false
    private var dialogSetCategory: String = "All"
    private var dialogSetMin: Float = 0f
    private var dialogSetMax: Float = 0f
    private var dialogSetSliderValuePositionX: Float? = null
    private var isSuperReviewFilterApplied = false
    private var reviewCountButtonText: String = "Reviews"
    private var isReviewCountButtonActive: Boolean = false

    // Results
    private lateinit var bottomSheet: LinearLayout
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<LinearLayout>
    private var isBottomSheetVisible = false
    private lateinit var resultsTitle: TextView
    private lateinit var clearResultsButton: ImageButton
    private lateinit var resultsSortButton: Button
    private lateinit var resultsContainer: LinearLayout
    private lateinit var scrollView: ScrollView
    private var filteredResults: List<PlaceData> = emptyList()
    private var sortedResults: List<PlaceData> = emptyList()
    private var originalResults: List<PlaceData> = emptyList()
    private var isSorted = false

    // Place
    private lateinit var placeDetailsLayout: LinearLayout
    private var previousState: String? = null
    private var previousBottomSheetState: Int? = null
    private lateinit var placeCategoryReviewsRatingView: TextView
    private lateinit var placeAddressView: TextView
    private lateinit var placeHoursView: TextView
    private lateinit var placePriceView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        // Typesafety
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = android.graphics.Color.TRANSPARENT
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or 
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        } else {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }

        // Connect to Google Places platform
        val apiKey = BuildConfig.PLACES_API_KEY

        if (apiKey.isEmpty() || apiKey == "DEFAULT_API_KEY") {
            Log.e("places test", "No api key")
            finish() // closes the activity
            return
        }

        // Get device location
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        
        // Initialize map fragment
        mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Initialize views
        searchView = binding.searchView
        myLocationButton = binding.myLocationButton
        reviewCountButton = binding.reviewCountButton
        superReviewButton = binding.superReviewButton
        loadingStateMessage = binding.loadingStateMessage
        loadingProgressText = binding.loadingProgressText
        bottomSheet = binding.bottomSheet
        resultsTitle = binding.resultsTitle
        clearResultsButton = binding.clearResultsButton
        resultsSortButton = binding.resultsSortButton
        resultsContainer = binding.resultsContainer
        scrollView = binding.scrollableSection
        placeDetailsLayout = binding.placeDetailsLayout
        placeCategoryReviewsRatingView = binding.placeCategoryReviewsRatingView
        placeAddressView = binding.placeAddressView
        placeHoursView = binding.placeHoursView
        placePriceView = binding.placePriceView

        // Initialize bottom sheet
        bottomSheetBehavior = BottomSheetBehavior.from(binding.bottomSheet)
        initBottomSheet()

        bottomSheet.viewTreeObserver.addOnGlobalLayoutListener {
            val locationButtonParams = myLocationButton.layoutParams as ViewGroup.MarginLayoutParams
            val locationButtonDefaultMarginBottom = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 40f, resources.displayMetrics).toInt()
            val locationButtonInitialMarginBottom = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 160f, resources.displayMetrics).toInt()
            
            val isVisible = bottomSheet.visibility == View.VISIBLE
            if (isVisible != isBottomSheetVisible) {
                if (!isVisible) {
                    locationButtonParams.bottomMargin = locationButtonDefaultMarginBottom
                } else {
                    locationButtonParams.bottomMargin = locationButtonInitialMarginBottom
                }
                myLocationButton.layoutParams = locationButtonParams
                isBottomSheetVisible = isVisible
            }
        }

        // Show/hide elements
        bottomSheet.visibility = View.GONE
        reviewCountButton.visibility = View.GONE
        superReviewButton.visibility = View.GONE
        resultsSortButton.visibility = View.GONE
        loadingStateMessage.visibility = View.GONE

        // My location button logic
        myLocationButton.setOnClickListener {
            moveToCurrentLocation()
        }

        // Sort button logic
        resultsSortButton.setOnClickListener {
            val resultsToSort = if (filteredResults.isNotEmpty()) {
                filteredResults
            } else {
                originalResults
            }

            if(isSorted) {
                // Reset sort
                addResultsToList(resultsToSort)
                updateButtonBackground(resultsSortButton, R.drawable.rounded_corners, R.color.colorBackground)
            } else {
                // Sort by review count high to low
                sortedResults = resultsToSort.sortedByDescending { it.userRatingsTotal ?: 0 }
                addResultsToList(sortedResults)
                updateButtonBackground(resultsSortButton, R.drawable.rounded_corners, R.color.colorButtonActive)
            }
            isSorted = !isSorted
        }

        // Loading progress logic
        handler = Handler(Looper.getMainLooper())

        progressRunnable = Runnable {
            handler.postDelayed(progressRunnable, 2000)
            updateLoadingProgress(loadingState)
            loadingState = (loadingState + 1) % 3
        }

        // Search view logic
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) : Boolean {
                Log.d("searchDebug", "Search submitted: $query")
                searchView.clearFocus()
                query?.let { 
                    if (checkLocationPermission()) {
                        mFusedLocationProviderClient.lastLocation.addOnSuccessListener { location: Location? ->
                            location?.let {
                                lastLatLng = LatLng(it.latitude, it.longitude)
                                lastQuery = query
                                resetReviewFilter()
                                showResultsView()
                                loadingStateMessage.visibility = View.VISIBLE
                                handler.post(progressRunnable)
                                searchForLocation(query)
                            }
                        }
                    }
                }
                return false
            }

            // reset features when user clears search field
            override fun onQueryTextChange(newText: String?): Boolean {
                if (newText.isNullOrEmpty()) {                    
                }
                return false
            }
        })
    }

    // Bottom sheet logic
    private fun initBottomSheet() {
        
        // Button heights
        val locationButtonInitialMarginBottom = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 160f, resources.displayMetrics).toInt()
        
        // Leave space above expanded sheet for search bar
        val bottomSheetOffset = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 90f, resources.displayMetrics).toInt()
        bottomSheetBehavior.expandedOffset = bottomSheetOffset

        // Set configurations
        bottomSheetBehavior.isHideable = false
        bottomSheetBehavior.halfExpandedRatio = 0.66f
        bottomSheetBehavior.isFitToContents = false

        // Clear search, results, and hide bottom sheet
        clearResultsButton.setOnClickListener {
            if (placeDetailsLayout.visibility == View.VISIBLE) {

                placeDetailsLayout.visibility = View.GONE
                showResultsView()
                showFilters()
                bottomSheetBehavior.state = previousBottomSheetState ?: BottomSheetBehavior.STATE_COLLAPSED
                markerPlaceMap.keys.forEach { marker -> 
                    updateMarkers(marker, isSelected = false)
                }
            } else {
                mMap.clear()
                searchView.setQuery("", false)
                resultsContainer.removeAllViews()
                resetReviewFilter()
                showResultsView()
                bottomSheet.visibility = View.GONE
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            }
        }

        // Can always drag sheet from its top
        resultsTitle.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                bottomSheetBehavior.isDraggable = true
            }
            false
        }

        // Disable dragging within scroll if scroll not at top
        scrollView.setOnTouchListener { _, event -> 
            if (event.action == MotionEvent.ACTION_MOVE && bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED) {
                if (scrollView.scrollY > 0) {
                    bottomSheetBehavior.isDraggable = false
                } else {
                    bottomSheetBehavior.isDraggable = true
                }
            }
            false
        }

        // Disable result scrolling unless bottom sheet fully expanded
        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                when (newState) {
                    BottomSheetBehavior.STATE_EXPANDED -> {
                        scrollView.isEnabled = true
                    }
                    BottomSheetBehavior.STATE_COLLAPSED -> {
                        scrollView.isEnabled = false
                        resultsSortButton.visibility = View.GONE
                    }
                    BottomSheetBehavior.STATE_HALF_EXPANDED -> {
                        scrollView.isEnabled = false
                    }
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                if (placeDetailsLayout.visibility != View.VISIBLE) {
                    if (slideOffset > 0) {
                        resultsSortButton.visibility = View.VISIBLE
                        resultsSortButton.alpha = slideOffset
                    } else {
                        resultsSortButton.visibility = View.GONE
                    }
                }
                
                if (slideOffset > 0) {
                    // move location button with sheet
                    val locationButtonParams = myLocationButton.layoutParams as ViewGroup.MarginLayoutParams
                    locationButtonParams.bottomMargin = (bottomSheet.height * slideOffset).toInt() + locationButtonInitialMarginBottom
                    myLocationButton.layoutParams = locationButtonParams

                    // fade out location button as sheet moves up
                    myLocationButton.alpha = 1 - slideOffset * 2
                    myLocationButton.isClickable = myLocationButton.alpha >= 0.99f
                }
            }
        })
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

        // Disable the default My Location button
        mMap.uiSettings.isMyLocationButtonEnabled = false

        // Request last location, move camera there
        moveToCurrentLocation(15f)
        
        // Location button changes when centered on user
        mMap.setOnCameraMoveListener {
            updateButtonState()
        }

        mMap.setOnMarkerClickListener { marker ->
            
            // Track previous state
            previousBottomSheetState = bottomSheetBehavior.state

            // Deselect all markers
            markerPlaceMap.keys.forEach { updateMarkers(it, isSelected = false) }

            // Select clicked marker
            updateMarkers(marker, isSelected = true)

            // Get place, expand sheet, and show details
            val place = markerPlaceMap[marker]
            place?.let {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED
                showPlaceDetails(it)
            }
            true
        }
    }

    // Location permission logic
    private fun checkLocationPermission(): Boolean {
            return if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // Request permission
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
                false
            } else {
                true
            }
        }

    // Return to user location logic
    private fun moveToCurrentLocation(zoom: Float? = null) {
        if (checkLocationPermission()) {
            mFusedLocationProviderClient.lastLocation
                .addOnSuccessListener { location: Location? -> 
                location?.let {
                    val currentLocation = LatLng(it.latitude, it.longitude)
                    Log.d("Location", "Current location: $currentLocation")

                    val currentZoom = zoom ?: mMap.cameraPosition.zoom

                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, currentZoom))
                    isCameraCentered = true
                    updateButtonState()
                }
            }
        }
    }

    // myLocationButton display
    private fun updateButtonState() {
        if (checkLocationPermission()) {
            val cameraPosition = mMap.cameraPosition.target
            val currentLocation = mFusedLocationProviderClient.lastLocation
            currentLocation.addOnSuccessListener { location: Location? ->
                location?.let {
                    val locationLatLng = LatLng(it.latitude, it.longitude)
                    
                    isCameraCentered = Math.abs(cameraPosition.latitude - locationLatLng.latitude) < 0.0001 &&
                                       Math.abs(cameraPosition.longitude - locationLatLng.longitude) < 0.0001
                    if (isCameraCentered) {
                        myLocationButton.setImageResource(R.drawable.ic_mylocation_centered)
                    } else {
                        myLocationButton.setImageResource(R.drawable.ic_mylocation_not_centered)
                    }
                }
            }
        }
    }

    // Search query logic
    private fun searchForLocation(query: String, minCount: Int = 0, maxCount: Int = Int.MAX_VALUE, moveCamera: Boolean = true, pageToken: String? = null, accumulatedResults: MutableList<PlaceData> = mutableListOf()) {

        val client = OkHttpClient()
        val apiKey = BuildConfig.PLACES_API_KEY

        // Get the map's current bounds
        val bounds = mMap.projection.visibleRegion.latLngBounds
        val southwest = bounds.southwest
        val northeast = bounds.northeast
        val center = bounds.center

        // Radius is width of viewport
        val results = FloatArray(1)
        Location.distanceBetween(
            southwest.latitude,
            southwest.longitude,
            southwest.latitude,
            northeast.longitude,
            results
        )
        val radius = (results[0] / 2).toInt()

        val url = "https://maps.googleapis.com/maps/api/place/textsearch/json?query=${URLEncoder.encode(query, "UTF-8")}&location=${center.latitude},${center.longitude}&radius=$radius&key=$apiKey${if (pageToken != null) "&pagetoken=$pageToken" else ""}"

        val request = Request.Builder()
            .url(url)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                loadingStateMessage.visibility = View.GONE
                Log.e("MainActivity", "Text search failed: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val myResponse = response.body?.string()

                    myResponse?.let {
                        val jsonResponse = JSONObject(it)
                        val results =jsonResponse.getJSONArray("results")

                        for (i in 0 until results.length()) {
                            val result = results.getJSONObject(i)
                            val place = PlaceData(
                                id = result.getString("place_id"),
                                name = result.getString("name"),
                                latLng = LatLng(
                                    result.getJSONObject("geometry").getJSONObject("location").getDouble("lat"),
                                    result.getJSONObject("geometry").getJSONObject("location").getDouble("lng")
                                ),
                                userRatingsTotal = result.optInt("user_ratings_total", 0),
                                avgRating = result.optDouble("rating", 0.0),
                                address = result.optString("formatted_address"),
                                hours = result.optJSONObject("opening_hours")?.optBoolean("open_now", false),
                                priceLevel = result.optInt("price_level", 0)
                            )
                            accumulatedResults.add(place)
                        }

                        val nextPageToken = jsonResponse.optString("next_page_token")
                        if (!nextPageToken.isNullOrEmpty()) {
                            Handler(Looper.getMainLooper()).postDelayed({
                                searchForLocation(query, minCount, maxCount, moveCamera, nextPageToken, accumulatedResults)
                            }, 2000)                
                        } else {
                            runOnUiThread {
                                if (accumulatedResults.isEmpty()) {
                                    // Hide loading state
                                    handler.removeCallbacks(progressRunnable)
                                    loadingStateMessage.visibility = View.GONE

                                    // Hide results sheet
                                    resultsContainer.removeAllViews()
                                    resetReviewFilter()
                                    showResultsView()
                                    bottomSheet.visibility = View.GONE
                                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED

                                    // Clear map  
                                    mMap.clear()

                                    showNoResultsDialog()

                                    return@runOnUiThread
                                }
                                
                                // Place markers with higher reviews are displayed above others
                                accumulatedResults.sortedByDescending { it.userRatingsTotal }

                                val reviewList = accumulatedResults.mapNotNull { it.userRatingsTotal }
                                val (calculateClusters, clusterRanges) = calculateClusters(reviewList)
                                clusters = calculateClusters

                                if (reviewList.isNotEmpty()) {
                                    val highestReviews = reviewList.maxOrNull() ?: 0
                                    roundedHighestReviews = ceil(highestReviews / 10.0).toFloat() * 10
                                    
                                    // Configure review count views
                                    updateButtonBackground(reviewCountButton, R.drawable.rounded_corners, R.color.colorBackground)
                                    reviewCountButton.setOnClickListener {
                                        showSliderDialog(roundedHighestReviews.toInt(), accumulatedResults, clusterRanges)
                                    }
                                    superReviewButton.setOnClickListener {
                                        toggleSuperReviewFilter(accumulatedResults, clusterRanges)
                                    }

                                    // Show review count views
                                    showFilters()
                                }

                                // Hide loading state
                                handler.removeCallbacks(progressRunnable)
                                loadingStateMessage.visibility = View.GONE

                                // Add map markers
                                addMarkersToMap(accumulatedResults, clusters, moveCamera)

                                // Add places to results list
                                addResultsToList(accumulatedResults)

                                // Display results bottom sheet
                                bottomSheet.visibility = View.VISIBLE
                            }
                        }
                    }
                }
            }
        })
    }

    data class PlaceData(
        val id: String,
        val name: String,
        val latLng: LatLng,
        val userRatingsTotal: Int?,
        val avgRating: Double?,
        val address: String?,
        val hours: Boolean?,
        val priceLevel: Int?
    )

    // Custom marker logic
    private fun createCustomMarker(context: Context, reviewCount: Int, category: String, isSelected: Boolean = false): BitmapDescriptor {
        // Create marker view
        val markerView = (context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater)
            .inflate(R.layout.custom_marker_layout, null)
        
        // Add review count and category
        val reviewCountTextView = markerView.findViewById<TextView>(R.id.review_count)
        val indicator = when (category) {
            "S" -> "🔥 "
            "H" -> "H "
            "M" -> "M "
            else -> "L "
        }
        reviewCountTextView.text = "$indicator$reviewCount"

        // Update marker style if selected or unselected
        val backgroundRes = if (isSelected) R.drawable.marker_selected_background else R.drawable.marker_default_background
        val textColorRes = if (isSelected) android.R.color.white else android.R.color.black

        reviewCountTextView.setBackgroundResource(backgroundRes)
        reviewCountTextView.setTextColor(ContextCompat.getColor(context, textColorRes))

        // Set arbitrary size and location
        markerView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        markerView.layout(0, 0, markerView.measuredWidth, markerView.measuredHeight)

        // Create container with right dimensions, draw onto 
        val bitmap = Bitmap.createBitmap(markerView.measuredWidth, markerView.measuredHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        markerView.draw(canvas)

        // Package as icon for google maps
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    // Add marker logic
    private fun addMarkersToMap(places: List<PlaceData>, clusters: Map<Int, String>, moveCamera:Boolean = true) {
        mMap.clear()
        markerPlaceMap.clear()

        // Initialize bounds
        val boundsBuilder = LatLngBounds.builder()

        // Add markers
        for (place in places) {
            val latLng = place.latLng
            val reviews = place.userRatingsTotal ?: 0
            val category = clusters[reviews] ?: "Low"
            if (latLng != null) {
                val isSelected = false
                val customMarker = createCustomMarker(this, reviews, category, isSelected)
                val marker = mMap.addMarker(MarkerOptions().position(latLng).title(place.name).icon(customMarker))
                markerPlaceMap[marker!!] = place
                boundsBuilder.include(latLng)
                }
            }

        // Fit map to the bounds
        if (moveCamera) {
            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 100))
        }
    }

    // Filter logic
    private fun filterReviewsbyCount(results: List<PlaceData>, minCount: Int, maxCount: Int) {
        // filter results
        filteredResults = results.filter { (it.userRatingsTotal ?: 0) in minCount..maxCount }
        
        // add places to map
        addMarkersToMap(filteredResults, clusters, moveCamera = false)

        // add results to list
        if (isSorted) {
            sortedResults = filteredResults.sortedByDescending { it.userRatingsTotal ?: 0 }
            addResultsToList(sortedResults)
        } else {
            addResultsToList(filteredResults)
        }
    }

    private fun filterReviewsbyCluster(results: List<PlaceData>, selectedClusterLabel: String, clusterRanges: List<ClusterRange>) {
        val selectedCluster = clusterRanges.find { it.label == selectedClusterLabel }
        
        // filter results
        filteredResults = if (selectedCluster != null) {
            results.filter { (it.userRatingsTotal ?: 0) in selectedCluster.min..selectedCluster.max }
        } else {
            results
        }
        
        // add places to map
        addMarkersToMap(filteredResults, clusters, moveCamera = false)
        
        // add results to list
        if (isSorted) {
            sortedResults = filteredResults.sortedByDescending { it.userRatingsTotal ?: 0 }
            addResultsToList(sortedResults)
        } else {
            addResultsToList(filteredResults)
        }
    }

    // Slider dialog logic
    private fun showSliderDialog(roundedHighestReviews: Int, results: List<PlaceData>, clusterRanges: List<ClusterRange>) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_slider, null)
        val bottomSheetDialog = BottomSheetDialog(this, R.style.FullWidthBottomSheetDialogTheme)
        val rangeSlider = dialogView.findViewById<com.google.android.material.slider.RangeSlider>(R.id.review_count_range_slider)
        val sliderValue = dialogView.findViewById<TextView>(R.id.slider_value)
        val numberOfPlacesText = dialogView.findViewById<TextView>(R.id.number_of_places_text)
        val cancelButton = dialogView.findViewById<Button>(R.id.slider_cancel_button)
        val doneButton = dialogView.findViewById<Button>(R.id.slider_done_button)
        val reviewCountClusterSelector = dialogView.findViewById<LinearLayout>(R.id.review_count_cluster_selector)

        bottomSheetDialog.setContentView(dialogView)

        updateReviewCountSummary(dialogView, clusterRanges)

        // Set slider range and steps
        rangeSlider.valueFrom = 0f
        rangeSlider.valueTo = roundedHighestReviews.toFloat()
        rangeSliderFrom = rangeSlider.valueFrom
        rangeSliderTo = rangeSlider.valueTo
        rangeSlider.stepSize = 10f 

        var placesInRange: Int
        var min: Float = 0f
        var max: Float = roundedHighestReviews.toFloat()

        // Display a button as selected if its category is selected
        val initialSelectedButton: TextView? = when (selectedCategory) {
            "M" -> dialogView.findViewById(R.id.cluster_m_button)
            "H" -> dialogView.findViewById(R.id.cluster_h_button)
            "S" -> dialogView.findViewById(R.id.cluster_s_button)
            "Manual" -> null 
            else -> dialogView.findViewById(R.id.cluster_all_button)
        }

        initialSelectedButton?.let {
            it.isSelected = true
            it.setCompoundDrawablesWithIntrinsicBounds(
                R.drawable.ic_check,
                0,
                0,
                0
            )
        }

        // Set the categories' min and max slider positions
        if (selectedCategory == "Manual") {
            min = previousMin
            max = previousMax
        } else if (selectedCategory == "All") {
            min = 0f
            max = roundedHighestReviews.toFloat()
        } else {
            val categoryRange = clusterRanges.find { it.label == selectedCategory }
            categoryRange?.let {
                min = it.min.toFloat()
                max = roundedHighestReviews.toFloat()
            }
        }
        rangeSlider.setValues(min, max)

        // Display slider range, position it midway between sliders
        sliderValue.text = "${min.toInt()} - ${max.toInt()}"
        
        sliderValue.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                sliderValue.viewTreeObserver.removeOnGlobalLayoutListener(this)
                
                sliderValuePositionX = calculateSliderValuePositionX(
                    min,
                    max,
                    rangeSlider.width,
                    rangeSlider.valueFrom,
                    rangeSlider.valueTo,
                    sliderValue.width
                )
                
                sliderValue.x = sliderValuePositionX?.toFloat() ?: 0.0f
            }
        })

        // Display number of places in range
        placesInRange = results.count { (it.userRatingsTotal ?: 0) in min.toInt()..max.toInt() }
        numberOfPlacesText.text = "$placesInRange ${if (placesInRange == 1) "place" else "places"}"

        var selectedTextView = initialSelectedButton // debug - might not need

        // Set sliders by clicking cluster buttons
        for (i in 0 until reviewCountClusterSelector.childCount step 2) {
            val clusterTextView: TextView = reviewCountClusterSelector.getChildAt(i) as TextView
            
            clusterTextView.setOnClickListener { view ->
                // Cannot deselect a button by clicking on it
                if (view.isSelected) {
                    return@setOnClickListener
                }

                // Clear previous selection
                selectedTextView?.isSelected = false
                selectedTextView?.setCompoundDrawablesWithIntrinsicBounds(0,0,0,0)

                // Set new selection
                view.isSelected = true
                selectedTextView = view as TextView
                view.setCompoundDrawablesWithIntrinsicBounds(
                    R.drawable.ic_check,
                    0,
                    0,
                    0
                )

                selectedCategory = when (view.id) {
                    R.id.cluster_all_button -> "All"
                    R.id.cluster_m_button -> "M"
                    R.id.cluster_h_button -> "H"
                    R.id.cluster_s_button -> "S"
                    else -> "All"
                }

                if (selectedCategory == "All") {
                    rangeSlider.setValues(0f, roundedHighestReviews.toFloat())
                } else {
                    val categoryRange = clusterRanges.find { it.label == selectedCategory }
                    categoryRange?.let {
                        rangeSlider.setValues(it.min.toFloat(), roundedHighestReviews.toFloat())
                    }
                }
            }
        }

        // Update displayed info as sliders move 
        rangeSlider.addOnChangeListener { slider, _, _ ->
            var min = slider.values[0]
            var max = slider.values[1] 

            // Enforce a minimum distance of 10 between thumbs
            val minimumDistance = 10f

            if (max - min < minimumDistance) {
                if (slider.activeThumbIndex == 0) {
                    // Left thumb is active, move it back
                    min = max - minimumDistance
                    slider.values = listOf(min, max)
                } else if (slider.activeThumbIndex == 1) {
                    // Right thumb is active, move it forward
                    max = min + minimumDistance
                    slider.values = listOf(min, max)
                }
            }

            // Update slider information
            sliderValue.x = calculateSliderValuePositionX(
                min,
                max,
                rangeSlider.width,
                rangeSlider.valueFrom,
                rangeSlider.valueTo,
                sliderValue.width
            )
            sliderValue.text = "${(min.toInt() / 10) * 10} - ${(max.toInt() / 10) * 10}"
            placesInRange = results.count { (it.userRatingsTotal ?: 0) in min.toInt()..max.toInt() }
            numberOfPlacesText.text = "$placesInRange ${if (placesInRange == 1) "place" else "places"}"
        }

        rangeSlider.addOnSliderTouchListener(object : RangeSlider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: RangeSlider) {
                if (selectedTextView?.isSelected == true) {
                    selectedTextView?.isSelected = false
                    selectedTextView?.setCompoundDrawablesWithIntrinsicBounds(0,0,0,0)
                    selectedCategory = "Manual"
                }
            }

            override fun onStopTrackingTouch(slider: RangeSlider) {

            }
        })

        cancelButton.setOnClickListener {
            bottomSheetDialog.dismiss()
        }

        doneButton.setOnClickListener {
            val minCount = rangeSlider.values[0].toInt()
            val maxCount = rangeSlider.values[1].toInt()
            
            // Filter by range
            filterReviewsbyCount(results, minCount, maxCount)

            // Display selected range in filter button
            if (minCount != 0 || maxCount != roundedHighestReviews.toInt()) {
                val roundedMinCount = (floor(minCount / 10.0) * 10).toInt()
                val roundedMaxCount = (ceil(maxCount / 10.0) * 10).toInt()
                reviewCountButton.text = "$roundedMinCount - $roundedMaxCount"
                updateButtonBackground(reviewCountButton, R.drawable.rounded_corners, R.color.colorButtonActive)
                reviewCountButton.setCompoundDrawablesWithIntrinsicBounds(
                    R.drawable.ic_check, 
                    0, 
                    R.drawable.ic_arrow_drop_down_black, 
                    0
                )
                reviewCountButtonText = reviewCountButton.text.toString()
                isReviewCountButtonActive = true
            // If full range selected, reset filter button display
            } else {
                reviewCountButton.text = "Reviews"
                updateButtonBackground(reviewCountButton, R.drawable.rounded_corners, R.color.colorBackground)
                reviewCountButton.setCompoundDrawablesWithIntrinsicBounds(
                    0, 
                    0, 
                    R.drawable.ic_arrow_drop_down_black, 
                    0
                )
                reviewCountButtonText = "Reviews"
                isReviewCountButtonActive = false
            }

            // Update super filter button
            if (selectedCategory == "S") {
                updateButtonBackground(superReviewButton, R.drawable.round_normal, R.color.colorButtonActive)
                isSuperReviewFilterApplied = true
            } else {
                updateButtonBackground(superReviewButton, R.drawable.round_normal, R.color.colorBackground)
                isSuperReviewFilterApplied = false
            }

            // Save states
            isDoneButtonClicked = true
            previousMin = rangeSlider.values[0]
            previousMax = rangeSlider.values[1]
            dialogSetCategory = selectedCategory
            dialogSetMin = previousMin
            dialogSetMax = previousMax
            dialogSetSliderValuePositionX = sliderValuePositionX

            bottomSheetDialog.dismiss()
        }

        bottomSheetDialog.show()
    }

   fun calculateClusters(reviewCounts: List<Int>, numClusters: Int = 4): Pair<Map<Int, String>, List<ClusterRange>> {
        val clusterLabels = listOf("S", "H", "M", "L")
        val clusterMap = mutableMapOf<Int, String>()
        val clusterRanges = mutableListOf<ClusterRange>()

    // No results
    if (reviewCounts.isEmpty()) {
        Log.d("calculateClusters", "Review counts are empty.")
        return Pair(mapOf(), listOf())
    }

    // Some results, but too few to cluster
    if (reviewCounts.size < numClusters) {
        Log.d("calculateClusters", "Not enough points to create $numClusters clusters.")
        noSuper = true
        val clusterMap = reviewCounts.associateWith { "M" }
        val clusterRanges = listOf(ClusterRange("M", reviewCounts.minOrNull() ?: 0, reviewCounts.maxOrNull() ?: 0))
        return Pair(clusterMap, clusterRanges)
    }

    // Convert review numbers to points for the clustering algorithm
    val points = reviewCounts.map { DoublePoint(doubleArrayOf(it.toDouble())) }
    
    // Select the clustering algorithm
    val clusterer = KMeansPlusPlusClusterer<DoublePoint>(numClusters)
    
    // Run the algorithm, sorting points into clusters
    val clusters: List<Cluster<DoublePoint>> = clusterer.cluster(points)

    // Get the average of each cluster
    val centroids = clusters.map { cluster ->
        cluster.points.map { it.point[0] }.average()
    }

    // Sort cluster by their average
    val sortedClusters = centroids.zip(clusters).sortedByDescending { it.first }

    for ((index, pair) in sortedClusters.withIndex()) {
        val cluster = pair.second
        val clusterLabel = clusterLabels[index]

        // Calculate each cluster's range
        val min = cluster.points.minOfOrNull { it.point[0].toInt() } ?: 0
        val max = cluster.points.maxOfOrNull { it.point[0].toInt() } ?: 0
        val roundedMin = (min / 100) * 100
        val roundedMax = ((max + 99) / 100) * 100

        // Save each range with its label
        clusterRanges.add(ClusterRange(clusterLabel, roundedMin, roundedMax))

        // Map each review count to its cluster label
        for (point in cluster.points) {
            clusterMap[point.point[0].toInt()] = clusterLabel
        }
    }

    noSuper = false
    return Pair(clusterMap, clusterRanges)
}


    private fun updateReviewCountSummary(dialogView: View, clusterRanges: List<ClusterRange>) {
        val summaryTitle = dialogView.findViewById<TextView>(R.id.summary_title)
        val summaryContent = dialogView.findViewById<LinearLayout>(R.id.summary_content)
        
        summaryContent.removeAllViews()

        if (clusterRanges.isNullOrEmpty()) {
            summaryTitle.visibility = View.GONE
            summaryContent.visibility = View.GONE
            return
        }

        summaryTitle.visibility = View.VISIBLE
        summaryContent.visibility = View.VISIBLE

        // Sort the ranges to appear top to bottom high to low
        val labelOrder = listOf("S", "H", "M", "L")
        val sortedRanges = clusterRanges.sortedBy { labelOrder.indexOf(it.label) }

        val summaryText = StringBuilder("Summary:\n")
        // Fill & display each range
        for (range in sortedRanges) {
            val textView = TextView(this)
            val label = if (range.label == "S") "🔥" else range.label
            textView.text = "$label: ${range.min} - ${range.max}"
            
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.gravity = Gravity.CENTER_HORIZONTAL
            textView.layoutParams = params

            val paddingBottomDp = 24
            val paddingLateralDp = 8
            val scale = resources.displayMetrics.density
            val paddingBottomPx = (paddingBottomDp * scale + 0.5f).toInt()
            val paddingLateralPx = (paddingLateralDp * scale + 0.5f).toInt()

            textView.setPadding(
                paddingLateralPx, 
                0, 
                paddingLateralPx, 
                paddingBottomPx
                )

            summaryContent.addView(textView)
        }
    }

    // No results dialog logic
    private fun showNoResultsDialog() {

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_noresults, null)
        val closeButton = dialogView.findViewById<Button>(R.id.no_results_close_button)

        val dialog = android.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        
        closeButton.setOnClickListener {
            searchView.setQuery("", false)
            searchView.clearFocus()
            dialog.dismiss()
        }

        dialog.show()

    }

    // Clear filter
    private fun resetReviewFilter() {
        reviewCountButton.visibility = View.GONE
        superReviewButton.visibility = View.GONE
        reviewCountButton.text = "Reviews"
        reviewCountButton.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_arrow_drop_down_black, 0)
        previousMin = 0f
        previousMax = 0f
    }

    // super review filter logic
    private fun toggleSuperReviewFilter(accumulatedResults: List<PlaceData>, clusterRanges: List<ClusterRange>) {
        
        isSuperReviewFilterApplied = !isSuperReviewFilterApplied

        if (isSuperReviewFilterApplied) {
            updateButtonBackground(superReviewButton, R.drawable.round_normal, R.color.colorButtonActive)
            filterReviewsbyCluster(accumulatedResults, "S", clusterRanges)
            selectedCategory = "S"
            val categoryRange = clusterRanges.find { it.label == selectedCategory }
            categoryRange?.let {
                val superMinCount = (floor(it.min / 10.0) * 10).toInt()
                val superMaxCount = (ceil(it.max / 10.0) * 10).toInt()
                reviewCountButton.text = "$superMinCount - $superMaxCount"
                updateButtonBackground(reviewCountButton, R.drawable.rounded_corners, R.color.colorButtonActive)
                reviewCountButton.setCompoundDrawablesWithIntrinsicBounds(
                    R.drawable.ic_check,
                    0,
                    R.drawable.ic_arrow_drop_down_black,
                    0
                )
            }
        } else {
            // Change display
            updateButtonBackground(superReviewButton, R.drawable.round_normal, R.color.colorBackground)
            
            // Revert results
            if (isDoneButtonClicked) {
                filterReviewsbyCount(accumulatedResults, dialogSetMin.toInt(), dialogSetMax.toInt())
            } else {
                filterReviewsbyCount(accumulatedResults, 0, roundedHighestReviews.toInt())
            }

            // Revert sliderDialog display
            selectedCategory = dialogSetCategory
            previousMin = dialogSetMin
            previousMax = dialogSetMax
            sliderValuePositionX = dialogSetSliderValuePositionX
            reviewCountButton.text = reviewCountButtonText
            updateButtonBackground(reviewCountButton, R.drawable.rounded_corners, if (isReviewCountButtonActive) R.color.colorButtonActive else R.color.colorBackground)
            if (isReviewCountButtonActive) {
                reviewCountButton.setCompoundDrawablesWithIntrinsicBounds(
                    R.drawable.ic_check,
                    0,
                    R.drawable.ic_arrow_drop_down_black,
                    0
                )
            } else {
                reviewCountButton.setCompoundDrawablesWithIntrinsicBounds(
                    0,
                    0,
                    R.drawable.ic_arrow_drop_down_black,
                    0
                )
            }
        }
    }

    //
    private fun updateLoadingProgress(state: Int) {
        when (state) {
            0 -> loadingProgressText.text = "🫶"
            1 -> loadingProgressText.text = "🫰"
            2 -> loadingProgressText.text = "👍"
        }
    }

    // Search results list logic
    private fun addResultsToList(results: List<PlaceData>) {
        if (originalResults.isEmpty()) {
            originalResults = results
        }

        // clear existing views
        resultsContainer.removeAllViews()

        var inflater = LayoutInflater.from(this)
        for (result in results) {
            val itemView = inflater.inflate(R.layout.item_search_result, resultsContainer, false)

            val placeNameTextView = itemView.findViewById<TextView>(R.id.place_name)
            val reviewCountTextView = itemView.findViewById<TextView>(R.id.review_count)

            placeNameTextView.text = result.name
            reviewCountTextView.text = "${result.userRatingsTotal} reviews"

            itemView.setOnClickListener {
                // Track previous state
                previousBottomSheetState = bottomSheetBehavior.state

                // Update marker, expand sheet, and show place details
                val marker = markerPlaceMap.entries.find { it.value.id == result.id }?.key

                marker?.let {
                    markerPlaceMap.keys.forEach { updateMarkers(it, isSelected = false) }

                    updateMarkers(it, isSelected = true)
                
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                    showPlaceDetails(result)
                }

            }

            resultsContainer.addView(itemView)
        }
    }

    private fun updateButtonBackground(button: Button, @DrawableRes drawableRes: Int, @ColorRes colorRes: Int) {
        val layerDrawable = ContextCompat.getDrawable(this, drawableRes) as LayerDrawable
        val solidShape = layerDrawable.findDrawableByLayerId(R.id.solid_shape) as GradientDrawable
        solidShape.setColor(ContextCompat.getColor(this, colorRes))
        button.background = layerDrawable
    }

    private fun showPlaceDetails(place: PlaceData) {
        // Highlight that place's marker
        markerPlaceMap.keys.forEach { marker ->
            updateMarkers(marker, isSelected = false)
            }

        val selectedMarker = markerPlaceMap.entries.find { it.value.id == place.id }?.key

        selectedMarker?.let {
            updateMarkers(it, isSelected = true)
        }
        
        val reviews = place.userRatingsTotal ?: 0
        val category = clusters[reviews] ?: "Low"
        val ratingText = place.avgRating?.toString() ?: ""

        // Insert values
        resultsTitle.text = place.name
        placeCategoryReviewsRatingView.text = "$category $reviews (⭐️$ratingText)"
        placeAddressView.text = place.address ?: "Address not available"
        placeHoursView.text = when (place.hours) {
            true -> "Open"
            false -> "Closed"
            else -> "Unclear if this place is open or closed right now"
        }
        placePriceView.text = when (place.priceLevel) {
            0 -> "$"
            1 -> "$$"
            2 -> "$$$"
            3 -> "$$$$"
            else -> "Price not available"
        }

        // Hide/show views
        reviewCountButton.visibility = View.GONE
        superReviewButton.visibility = View.GONE
        scrollView.visibility = View.GONE
        Log.d("VisibilityCheck", "Hiding sort button")
        resultsSortButton.visibility = View.GONE
        Log.d("VisibilityCheck", "Sort button visibility: ${resultsSortButton.visibility}")
        placeDetailsLayout.visibility = View.VISIBLE
    }

    private fun updateMarkers(marker: Marker, isSelected: Boolean) {
        resetSelectedMarker()
        if (isSelected) {
            updateMarkerIcon(marker, true)
            selectedMarker = marker
        }
    }

    private fun resetSelectedMarker() {
        selectedMarker?.let {
            updateMarkerIcon(it, isSelected = false)
            selectedMarker = null
        }
    }
    
    private fun updateMarkerIcon(marker: Marker, isSelected: Boolean) {
        val place = markerPlaceMap[marker]
        place?.let {
            val reviews = it.userRatingsTotal ?: 0
            val category = clusters[reviews] ?: "Low"
            val customMarker = createCustomMarker(this, it.userRatingsTotal ?: 0, category, isSelected)
            marker.setIcon(customMarker)
        }
    }

    private fun showResultsView() {
        resultsTitle.text = "Results"
        if (bottomSheetBehavior.state != BottomSheetBehavior.STATE_COLLAPSED) {
            resultsSortButton.visibility = View.VISIBLE
        } else {
            resultsSortButton.visibility = View.GONE
        }
        scrollView.visibility = View.VISIBLE
        placeDetailsLayout.visibility = View.GONE
        resetSelectedMarker()
    }

    private fun showFilters() {
        reviewCountButton.visibility = View.VISIBLE
        if (!noSuper) {
            superReviewButton.visibility = View.VISIBLE
        }
    }

    private fun calculateSliderValuePositionX(min: Float, max: Float, sliderWidth: Int, valueFrom: Float, valueTo: Float, sliderValueWidth: Int): Float {
        var output = 0f
        
        val leftThumbPos = sliderWidth * ((min - valueFrom) / (valueTo - rangeSliderFrom))
        val rightThumbPos = sliderWidth * ((max - valueFrom) / (valueTo - rangeSliderFrom))
        val midpoint = (leftThumbPos + rightThumbPos) / 2

        val maxAllowedPosition = sliderWidth - sliderValueWidth
        val adjustedPosition = midpoint - sliderValueWidth / 2

        output = when {
            adjustedPosition < 0 -> 0f
            adjustedPosition > maxAllowedPosition -> maxAllowedPosition.toFloat()
            else -> adjustedPosition
        }

        return output
    }
}