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
import androidx.appcompat.app.AlertDialog

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

// Filter
import com.jaygoo.widget.RangeSeekBar
import kotlin.math.ceil
import kotlin.math.floor
import org.apache.commons.math3.ml.clustering.Cluster
import org.apache.commons.math3.ml.clustering.Clusterable
import org.apache.commons.math3.ml.clustering.KMeansPlusPlusClusterer
import org.apache.commons.math3.ml.clustering.DoublePoint

data class ClusterRange(val label: String, val min: Int, val max: Int)

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
    private var noResultsDialog: AlertDialog? = null
    private lateinit var loadingStateMessage: LinearLayout
    private lateinit var loadingProgressText: TextView
    private var fetchCount = 0

    // Reviews
    private lateinit var reviewCountButton: Button
    private lateinit var superReviewButton: Button
    private lateinit var reviewCountSummary: LinearLayout
    private lateinit var clusters: Map<Int, String>

    // Filter
    private var previousMin: Float = 0f
    private var previousMax: Float = 0f
    private var isSuperReviewFilterApplied = false

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

        // Get device location
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        
        // Initialize map fragment
        mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Initialize views
        searchView = binding.searchView
        reviewCountButton = binding.reviewCountButton
        superReviewButton = binding.superReviewButton
        reviewCountSummary = binding.reviewCountSummary
        loadingStateMessage = binding.loadingStateMessage
        loadingProgressText = binding.loadingProgressText
        
        // Show/hide elements
        reviewCountButton.visibility = View.GONE
        superReviewButton.visibility = View.GONE
        reviewCountSummary.visibility = View.GONE
        loadingStateMessage.visibility = View.GONE

        // Search view logic
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) : Boolean {
                query?.let { 
                    if (checkLocationPermission()) {
                        mFusedLocationProviderClient.lastLocation.addOnSuccessListener { location: Location? ->
                            location?.let {
                                lastLatLng = LatLng(it.latitude, it.longitude)
                                lastQuery = query
                                resetReviewFilter()
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
                    mMap.clear()
                    resetReviewFilter()
                }

                return false
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

        // Enable zoom controls for debugging
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
        
        // locationButtonParent.background = ContextCompat.getDrawable(this, R.drawable.border) // debug

        val locationButton = locationButtonParent.findViewById<View>("2".toInt())
        val rlp = locationButton.layoutParams as RelativeLayout.LayoutParams
        rlp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE)
        rlp.addRule(RelativeLayout.ALIGN_PARENT_END, RelativeLayout.TRUE)
        rlp.setMargins(0, 0, 16, 100) // Adjust margins as needed
        locationButton.layoutParams = rlp
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

        Log.d("MainActivity", "Request URL: $url")

        Log.d("MainActivity", "Map bounds: NE(${bounds.northeast.latitude}, ${bounds.northeast.longitude}), SW(${bounds.southwest.latitude}, ${bounds.southwest.longitude})")

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

                    // display loading state
                    runOnUiThread {
                        loadingStateMessage.visibility = View.VISIBLE
                    }

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
                                userRatingsTotal = result.optInt("user_ratings_total", 0)
                            )
                            accumulatedResults.add(place)
                        }

                        val nextPageToken = jsonResponse.optString("next_page_token")
                        if (!nextPageToken.isNullOrEmpty()) {
                            fetchCount = fetchCount + 1
                            runOnUiThread {
                                updateLoadingProgress()
                            }
                            Handler(Looper.getMainLooper()).postDelayed({
                                searchForLocation(query, minCount, maxCount, moveCamera, nextPageToken, accumulatedResults)
                            }, 2000)                
                        } else {
                            runOnUiThread {
                                if (accumulatedResults.isEmpty()) {
                                    showNoResultsDialog()
                                    return@runOnUiThread
                                }

                                for (place in accumulatedResults) {
                                Log.d("MainActivity", "Place: ${place.name}, Location: (${place.latLng.latitude}, ${place.latLng.longitude}), Ratings: ${place.userRatingsTotal}")
                                }

                                accumulatedResults.sortedByDescending { it.userRatingsTotal }

                                val reviewList = accumulatedResults.mapNotNull { it.userRatingsTotal }
                                val (calculateClusters, clusterRanges) = calculateClusters(reviewList)
                                clusters = calculateClusters

                                if (reviewList.isNotEmpty()) {
                                    val highestReviews = reviewList.maxOrNull() ?: 0
                                    val roundedHighestReviews = ceil(highestReviews / 10.0) * 10

                                    reviewCountButton.visibility = View.VISIBLE
                                    superReviewButton.visibility = View.VISIBLE
                                    reviewCountSummary.visibility = View.VISIBLE

                                    reviewCountButton.setOnClickListener {
                                        showSliderDialog(roundedHighestReviews.toInt(), accumulatedResults)
                                    }

                                    updateReviewCountSummary(clusterRanges)

                                    superReviewButton.setOnClickListener {
                                        toggleSuperReviewFilter(accumulatedResults, clusterRanges)
                                    }

                                }

                                // Hide loading state
                                loadingStateMessage.visibility = View.GONE
                                loadingProgressText.text = "✊"

                                // Add map markers
                                addMarkersToMap(accumulatedResults, clusters, moveCamera)
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
        val userRatingsTotal: Int
    )

    // Custom marker logic
    private fun createCustomMarker(context: Context, reviewCount: Int, category: String): BitmapDescriptor {
        // create marker view
        val markerView = (context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater)
            .inflate(R.layout.custom_marker_layout, null)
        
        // add review count and category
        val reviewCountTextView = markerView.findViewById<TextView>(R.id.review_count)
        val indicator = when (category) {
            "S" -> "🔥 "
            "H" -> "H "
            "M" -> "M "
            else -> "L "
        }
        reviewCountTextView.text = "$indicator$reviewCount"

        // set arbitrary size and location
        markerView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        markerView.layout(0, 0, markerView.measuredWidth, markerView.measuredHeight)

        // create container with right dimensions, draw onto 
        val bitmap = Bitmap.createBitmap(markerView.measuredWidth, markerView.measuredHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        markerView.draw(canvas)

        // package as icon for google maps
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    // Add marker logic
    private fun addMarkersToMap(places: List<PlaceData>, clusters: Map<Int, String>, moveCamera:Boolean = true) {
        mMap.clear()

        // Initialize bounds
        val boundsBuilder = LatLngBounds.builder()

        // Add markers
        for (place in places) {

            Log.i("MainActivity", place.id ?: "") // debug
            Log.i("MainActivity", place.name ?: "") // debug

            val latLng = place.latLng
            val reviews = place.userRatingsTotal ?: 0
            val category = clusters[reviews] ?: "Low"
            if (latLng != null) {
                val customMarker = createCustomMarker(this, reviews, category)
                mMap.addMarker(MarkerOptions().position(latLng).title(place.name).icon(customMarker))
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
        val filteredResults = results.filter { (it.userRatingsTotal ?: 0) in minCount..maxCount }
        addMarkersToMap(filteredResults, clusters, moveCamera = false)
    }

    private fun filterReviewsbyCluster(results: List<PlaceData>, selectedClusterLabel: String, clusterRanges: List<ClusterRange>) {
        val selectedCluster = clusterRanges.find { it.label == selectedClusterLabel }
        val filteredResults = if (selectedCluster != null) {
            results.filter { (it.userRatingsTotal ?: 0) in selectedCluster.min..selectedCluster.max }
        } else {
            results
        }
        addMarkersToMap(filteredResults, clusters, moveCamera = false)
    }

    // Slider dialog logic
    private fun showSliderDialog(roundedHighestReviews: Int, results: List<PlaceData>) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_slider, null)
        val rangeSlider = dialogView.findViewById<com.jaygoo.widget.RangeSeekBar>(R.id.review_count_range_slider)
        val sliderValue = dialogView.findViewById<TextView>(R.id.slider_value)
        val numberOfPlacesText = dialogView.findViewById<TextView>(R.id.number_of_places_text)
        val cancelButton = dialogView.findViewById<Button>(R.id.slider_cancel_button)
        val doneButton = dialogView.findViewById<Button>(R.id.slider_done_button)

        val dialog = android.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        
        // set slider range and steps
        rangeSlider.setRange(0f, roundedHighestReviews.toFloat())
        rangeSlider.setSteps(10)

        var placesInRange: Int

        // set slider buttons and display information
        if (previousMin != 0f || previousMax != 0f) {
            rangeSlider.setProgress(previousMin, previousMax)
            sliderValue.text = "Range: ${previousMin.toInt()} - ${previousMax.toInt()}"
            placesInRange = results.count { (it.userRatingsTotal ?: 0) in previousMin.toInt()..previousMax.toInt() }

        } else {
            rangeSlider.setProgress(0f, roundedHighestReviews.toFloat())
            sliderValue.text = "Range: 0 - ${roundedHighestReviews.toInt()}"
            placesInRange = results.count { (it.userRatingsTotal ?: 0) in 0..roundedHighestReviews.toInt() }
        }

        numberOfPlacesText.text = "Places in range: $placesInRange"

        // prevent sliders passing each other, update displayed info as sliders move 
        rangeSlider.setOnRangeChangedListener(object : com.jaygoo.widget.OnRangeChangedListener {
            override fun onRangeChanged (
                view: com.jaygoo.widget.RangeSeekBar,
                min: Float,
                max: Float,
                isFromUser: Boolean
            ) {
                if (isFromUser) {
                    if (max - min < 10) {
                        if (min != previousMin) {
                            view.setProgress(max - 10, max)
                        } else if (max != previousMax) {
                            view.setProgress(min, min + 10)
                        }
                    } else {
                        previousMin = min
                        previousMax = max
                    }
                }
                sliderValue.text = "Range: ${(min.toInt() / 10) * 10} - ${(max.toInt() / 10) * 10}"

                placesInRange = results.count { (it.userRatingsTotal ?: 0) in min.toInt()..max.toInt() }
                numberOfPlacesText.text = "Places in range: $placesInRange"
            }

            override fun onStartTrackingTouch(view: com.jaygoo.widget.RangeSeekBar?, isLeft: Boolean) {
                previousMin = view?.leftSeekBar?.progress ?: 0f
                previousMax = view?.rightSeekBar?.progress ?: 0f
            }

            override fun onStopTrackingTouch(view: com.jaygoo.widget.RangeSeekBar?, isLeft: Boolean) {}
        })

        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        doneButton.setOnClickListener {
            
            // filter by range
            val minCount = rangeSlider.leftSeekBar.progress.toInt()
            val maxCount = rangeSlider.rightSeekBar.progress.toInt()

            Log.d("MainActivity", "Filtering with minCount: $minCount, maxCount: $maxCount")
            Log.d("MainActivity", "Clusters: $clusters")

            filterReviewsbyCount(results, minCount, maxCount)

            // display selected range
            if (rangeSlider.leftSeekBar.progress != 0f || rangeSlider.rightSeekBar.progress != roundedHighestReviews.toFloat()) {
                val roundedMinCount = (floor(minCount / 10.0) * 10).toInt()
                val roundedMaxCount = (ceil(maxCount / 10.0) * 10).toInt()
                reviewCountButton.text = "$roundedMinCount - $roundedMaxCount"
                reviewCountButton.setCompoundDrawablesWithIntrinsicBounds(
                    R.drawable.ic_check, 
                    0, 
                    R.drawable.ic_arrow_drop_down_black, 
                    0
                )
            } else {
                reviewCountButton.text = "Reviews"
                reviewCountButton.setCompoundDrawablesWithIntrinsicBounds(
                    0, 
                    0, 
                    R.drawable.ic_arrow_drop_down_black, 
                    0
                )
            }

            dialog.dismiss()
        }

        dialog.show()
    }

    fun calculateClusters(reviewCounts: List<Int>, numClusters: Int = 4): Pair<Map<Int, String>, List<ClusterRange>> {
        if (reviewCounts.isEmpty()) {
            return Pair(mapOf(), listOf())
        }

        val points = reviewCounts.map { DoublePoint(doubleArrayOf(it.toDouble())) }
        val clusterer = KMeansPlusPlusClusterer<DoublePoint>(numClusters)
        val clusters: List<Cluster<DoublePoint>> = clusterer.cluster(points)

        val centroids = clusters.map { cluster ->
        cluster.points.map { it.point[0] }.average()
        }

        val sortedClusters = centroids.zip(clusters).sortedByDescending { it.first }

        val labels = listOf("S", "H", "M", "L")

        val clusterMap = mutableMapOf<Int, String>()
        val clusterRanges = mutableListOf<ClusterRange>()

        for ((index, pair) in sortedClusters.withIndex()) {
            val cluster = pair.second
            val label = labels[index]
            val min = cluster.points.minOf { it.point[0].toInt() }
            val max = cluster.points.maxOf { it.point[0].toInt() }
            val roundedMin = (min / 100) * 100
            val roundedMax = ((max + 99) / 100) * 100
            clusterRanges.add(ClusterRange(label, roundedMin, roundedMax))


            for (point in cluster.points) {
                clusterMap[point.point[0].toInt()] = label
            }
        }

        return Pair(clusterMap, clusterRanges)
    }

    private fun updateReviewCountSummary(clusterRanges: List<ClusterRange>) {
        reviewCountSummary.removeAllViews()

        val labelOrder = listOf("S", "H", "M", "L")

        val sortedRanges = clusterRanges.sortedBy { labelOrder.indexOf(it.label) }

        for (range in sortedRanges) {
            val textView = TextView(this)
            textView.text = "${range.label}: ${range.min} - ${range.max}"
            reviewCountSummary.addView(textView)
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
        
        if (dialog.isShowing == true) {
            Log.d("MainActivity", "No results dialog already showing")
            return
        }

        closeButton.setOnClickListener {
            Log.d("MainActivity", "Close button clicked")
            searchView.setQuery("", false)
            searchView.clearFocus()
            Log.d("MainActivity", "Search cleared")
            dialog.dismiss()
            Log.d("MainActivity", "Dialog dismissed")
        }

        dialog.show()
        Log.d("MainActivity", "No results dialog opened")
    }

    // Clear filter
    private fun resetReviewFilter() {
        reviewCountButton.visibility = View.GONE
        superReviewButton.visibility = View.GONE
        reviewCountSummary.visibility = View.GONE
        reviewCountButton.text = "Reviews"
        reviewCountButton.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_arrow_drop_down_black, 0)
        previousMin = 0f
        previousMax = 0f
    }

    //
    private fun toggleSuperReviewFilter(accumulatedResults: List<PlaceData>, clusterRanges: List<ClusterRange>) {
        isSuperReviewFilterApplied = !isSuperReviewFilterApplied

        if (isSuperReviewFilterApplied) {
            superReviewButton.background = ContextCompat.getDrawable(this, R.drawable.round_active)
            filterReviewsbyCluster(accumulatedResults, "S", clusterRanges)
        } else {
            superReviewButton.background = ContextCompat.getDrawable(this, R.drawable.round_normal)
            addMarkersToMap(accumulatedResults, clusters, moveCamera = false)
        }
    }

    //
    private fun updateLoadingProgress() {
        if (fetchCount == 1) {
            loadingProgressText.text = "✊✋"
        } else if (fetchCount == 2) {
            loadingProgressText.text = "✊✋✌️"
        } else {
            loadingProgressText.text = "✊"
        }
    }

}