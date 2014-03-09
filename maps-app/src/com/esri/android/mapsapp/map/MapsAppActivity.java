/* Copyright 1995-2013 Esri
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * For additional information, contact:
 * Environmental Systems Research Institute, Inc.
 * Attn: Contracts Dept
 * 380 New York Street
 * Redlands, California, USA 92373
 *
 * email: contracts@esri.com
 *
 */

package com.esri.android.mapsapp.map;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationListener;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.Toast;

import com.esri.android.map.GraphicsLayer;
import com.esri.android.map.LocationDisplayManager;
import com.esri.android.map.LocationDisplayManager.AutoPanMode;
import com.esri.android.map.MapOnTouchListener;
import com.esri.android.map.MapView;
import com.esri.android.map.event.OnStatusChangedListener;
import com.esri.android.mapsapp.R;
import com.esri.android.mapsapp.basemaps.BasemapsDialogFragment;
import com.esri.android.mapsapp.basemaps.BasemapsDialogFragment.BasemapsDialogListener;
import com.esri.android.mapsapp.location.DirectionsDialogFragment;
import com.esri.android.mapsapp.location.DirectionsDialogFragment.DirectionsDialogListener;
import com.esri.core.geometry.Envelope;
import com.esri.core.geometry.Geometry;
import com.esri.core.geometry.GeometryEngine;
import com.esri.core.geometry.LinearUnit;
import com.esri.core.geometry.Point;
import com.esri.core.geometry.Polyline;
import com.esri.core.geometry.SpatialReference;
import com.esri.core.geometry.Unit;
import com.esri.core.map.Graphic;
import com.esri.core.portal.WebMap;
import com.esri.core.symbol.PictureMarkerSymbol;
import com.esri.core.symbol.SimpleLineSymbol;
import com.esri.core.symbol.SimpleLineSymbol.STYLE;
import com.esri.core.symbol.SimpleMarkerSymbol;
import com.esri.core.symbol.TextSymbol;
import com.esri.core.tasks.geocode.Locator;
import com.esri.core.tasks.geocode.LocatorFindParameters;
import com.esri.core.tasks.geocode.LocatorGeocodeResult;
import com.esri.core.tasks.geocode.LocatorReverseGeocodeResult;
import com.esri.core.tasks.na.NAFeaturesAsFeature;
import com.esri.core.tasks.na.Route;
import com.esri.core.tasks.na.RouteParameters;
import com.esri.core.tasks.na.RouteResult;
import com.esri.core.tasks.na.RouteTask;
import com.esri.core.tasks.na.StopGraphic;

/**
 * Entry point into the Maps App.
 */
public class MapsAppActivity extends Activity implements BasemapsDialogListener, DirectionsDialogListener {

  private static final String TAG = "MapsAppActivity";

  private static final String KEY_IS_LOCATION_TRACKING = "IsLocationTracking";

  MapView mMapView = null;

  String mMapViewState;

  boolean mIsLocationTracking;

  int basemap;

  LocatorGeocodeResult geocodeResult;

  // graphics layer to show geocode result
  GraphicsLayer mLocationLayer;

  // GPS Location definitions
  Point mLocation = null;

  // The circle area specified by search_radius and input lat/lon serves
  // searching purpose. It is also used to construct the extent which
  // map zooms to after the first GPS fix is retrieved.
  final static double SEARCH_RADIUS = 10;

  // Spatial references used for projecting points
  final SpatialReference wm = SpatialReference.create(102100);

  final SpatialReference egs = SpatialReference.create(4326);

  // create UI components
  static ProgressDialog mProgressDialog;

  // EditText widget for entering search items
  EditText mSearchEditText;

  // Strings for routing
  String startText;

  String endText;

  Point routePnt;

  // routing result definition
  RouteResult routeResult;

  // route definition
  Route route;

  RouteTask routeTask;

  String routeSummary;

  // graphics layer to show routes
  GraphicsLayer mRouteLayer;

  MotionEvent mLongPressEvent;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    if (savedInstanceState == null) {
      mIsLocationTracking = false;
    } else {
      mIsLocationTracking = savedInstanceState.getBoolean(KEY_IS_LOCATION_TRACKING);
    }

    // Create MapView to show the recreation WebMap
    String defaultBaseMapURL = getString(R.string.topo_basemap_url);
    mMapView = new MapView(this, defaultBaseMapURL, "", "");
    mLocationLayer = null;
    mRouteLayer = null;

    // Complete setup of MapView and set it as the content view
    setMapView(mMapView);

    // Setup progress dialog
    mProgressDialog = new ProgressDialog(this) {
      @Override
      public void onBackPressed() {
        // Back key pressed - just dismiss the dialog
        mProgressDialog.dismiss();
      }
    };

  }

  /**
   * Takes a MapView that has already been instantiated to show a WebMap, completes its setup by setting various
   * listeners and attributes, and sets it as the activity's content view.
   * 
   * @param mapView
   */
  private void setMapView(MapView mapView) {
    if (mapView == mMapView) {
      mMapViewState = null;
    } else {
      mMapViewState = mMapView.retainState();

      // Remove layers so they can be added to the new MapView
      mMapView.removeLayer(mLocationLayer);
      mMapView.removeLayer(mRouteLayer);

      // Need this to ensure old MapView's resources are freed up and location tracking is disabled.
      // Maybe unnecessary after MapView implementation is fixed.
      mMapView.getLocationDisplayManager().stop();
      mMapView.recycle();
    }
    mMapView = mapView;
    // attribute ESRI logo to map
    mMapView.setEsriLogoVisible(true);
    // enable map to wrap around date line
    mMapView.enableWrapAround(true);
    mMapView.setId(R.id.map); // Used in onBackPressed()

    setContentView(mMapView);

    // Setup listener for map initialized
    mMapView.setOnStatusChangedListener(new OnStatusChangedListener() {

      private static final long serialVersionUID = 1L;

      @Override
      public void onStatusChanged(Object source, STATUS status) {
        Log.i(TAG, "MapView.setOnStatusChangedListener() status=" + status.toString());
        if (source == mMapView && status == STATUS.INITIALIZED) {
          if (mMapViewState == null) {
            /*
             * // Set initial extent SpatialReference mSR = SpatialReference.create(3857); Point p1 =
             * GeometryEngine.project(-120.0, 0.0, mSR); Point p2 = GeometryEngine.project(-60.0, 50.0, mSR); Envelope
             * initExtent = new Envelope(p1.getX(), p1.getY(), p2.getX(), p2.getY()); mMapView.setExtent(initExtent);
             */

            // Starting location tracking will cause zoom to My Location
            startLocationTracking();
          } else {
            mMapView.restoreState(mMapViewState);
          }
          // add search and routing layers
          addGraphicLayers();
        }
      }
    });

    // Setup use of magnifier on a long press on the map
    mMapView.setShowMagnifierOnLongPress(true);
    mLongPressEvent = null;
    /*
     * This seems to stop the magnifier working: mMapView.setOnLongPressListener(new OnLongPressListener() { private
     * static final long serialVersionUID = 1L;
     * @Override public boolean onLongPress(float x, float y) { Point mapPoint = mMapView.toMapPoint(x, y); new
     * ReverseGeocoding(MapsAppActivity.this, mMapView).execute(mapPoint); return true; } });
     */

    mMapView.setOnTouchListener(new MapOnTouchListener(this, mMapView) {
      @Override
      public boolean onTouch(View v, MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
          // Start of a new gesture. Make sure mLongPressEvent is cleared.
          mLongPressEvent = null;
        }
        return super.onTouch(v, event);
      }

      @Override
      public void onLongPress(MotionEvent point) {
        // Set mLongPressEvent to indicate we are processing a long-press
        mLongPressEvent = point;
        super.onLongPress(point);
      }

      @Override
      public boolean onDragPointerUp(MotionEvent from, final MotionEvent to) {
        if (mLongPressEvent != null) {
          // This is the end of a long-press that will have displayed the magnifier.
          Point mapPoint = mMapView.toMapPoint(to.getX(), to.getY());
          new ReverseGeocodingAsyncTask().execute(mapPoint);
          mLongPressEvent = null;
          // remove any previous graphics, callouts and routes
          mLocationLayer.removeAll();
          mRouteLayer.removeAll();
        }
        return super.onDragPointerUp(from, to);
      }

    });

  }

  private void startLocationTracking() {
    LocationDisplayManager locDispMgr = mMapView.getLocationDisplayManager();
    locDispMgr.setAutoPanMode(AutoPanMode.OFF);
    locDispMgr.setAllowNetworkLocation(true); // TODO: why doesn't this seem to work?
    locDispMgr.setLocationListener(new LocationListener() {

      boolean locationChanged = false;

      // Zooms to the current location when first GPS fix
      // arrives.
      @Override
      public void onLocationChanged(Location loc) {
        if (!locationChanged) {
          locationChanged = true;
          double locy = loc.getLatitude();
          double locx = loc.getLongitude();
          Point wgspoint = new Point(locx, locy);
          mLocation = (Point) GeometryEngine.project(wgspoint, SpatialReference.create(4326),
              mMapView.getSpatialReference());

          Unit mapUnit = mMapView.getSpatialReference().getUnit();
          double zoomWidth = Unit.convertUnits(SEARCH_RADIUS, Unit.create(LinearUnit.Code.MILE_US), mapUnit);
          Envelope zoomExtent = new Envelope(mLocation, zoomWidth, zoomWidth);
          mMapView.setExtent(zoomExtent);
        }
      }

      @Override
      public void onProviderDisabled(String arg0) {
      }

      @Override
      public void onProviderEnabled(String arg0) {
      }

      @Override
      public void onStatusChanged(String arg0, int arg1, Bundle arg2) {
      }
    });
    locDispMgr.start();
    mIsLocationTracking = true;
  }

  void addGraphicLayers() {
    // Add location layer
    if (mLocationLayer == null) {
      mLocationLayer = new GraphicsLayer();
    }
    mMapView.addLayer(mLocationLayer);

    // Add the route graphic layer
    if (mRouteLayer == null) {
      mRouteLayer = new GraphicsLayer();
    }
    mMapView.addLayer(mRouteLayer);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    // Inflate the menu items for use in the action bar
    getMenuInflater().inflate(R.menu.actions, menu);
    // Get a reference to the EditText widget for the search option
    View searchRef = menu.findItem(R.id.menu_search).getActionView();
    mSearchEditText = (EditText) searchRef.findViewById(R.id.searchText);
    return super.onCreateOptionsMenu(menu);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {

    switch (item.getItemId()) {
      case R.id.route:
        // Show DirectionsDialogFragment to get routing start and end points.
        // This calls back to onGetDirections() to do the routing.
        DirectionsDialogFragment directionsFrag = new DirectionsDialogFragment();
        directionsFrag.setDirectionsDialogListener(this);
        directionsFrag.show(getFragmentManager(), null);
        return true;

      case R.id.basemaps:
        // Show BasemapsDialogFragment to offer a choice if basemaps.
        // This calls back to onBasemapChanged() if one is selected.
        BasemapsDialogFragment basemapsFrag = new BasemapsDialogFragment();
        basemapsFrag.setBasemapsDialogListener(this);
        basemapsFrag.show(getFragmentManager(), null);
        return true;

      case R.id.location:
        if (mIsLocationTracking) {
          mMapView.getLocationDisplayManager().stop();
          mIsLocationTracking = false;
        } else {
          startLocationTracking();
        }
        return true;

      default:
        return super.onOptionsItemSelected(item);
    }
  }

  @Override
  public void onBackPressed() {
    // Redisplay map view if back pressed on any other view
    if (findViewById(R.id.map) == null) {
      setContentView(mMapView); // FIXME doesn't work, fix once activity/fragment architecture firmed up
    } else {
      super.onBackPressed();
    }
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
  }

  @Override
  protected void onPause() {
    super.onPause();

    // Pause the MapView and stop the LocationDisplayManager to save battery
    if (mIsLocationTracking) {
      mMapView.getLocationDisplayManager().stop();
    }
    mMapViewState = mMapView.retainState();
    mMapView.pause();
  }

  @Override
  protected void onResume() {
    super.onResume();

    // Start the MapView and LocationDisplayManager running again
    mMapView.unpause();
    if (mMapViewState != null) {
      mMapView.restoreState(mMapViewState);
    }
    if (mIsLocationTracking) {
      mMapView.getLocationDisplayManager().start();
    }
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);

    outState.putBoolean(KEY_IS_LOCATION_TRACKING, mIsLocationTracking);
  }

  @Override
  public void onBasemapChanged(WebMap webMap) {
    MapView mapView = new MapView(this, webMap, null, null);
    setMapView(mapView);
  }

  /**
   * Called from search_layout.xml when user presses Search button.
   * 
   * @param view
   */
  public void onSearchButtonClicked(View view) {
    // Hide virtual keyboard
    InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
    inputManager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);

    // remove any previous graphics, callouts and routes
    mLocationLayer.removeAll();
    mRouteLayer.removeAll();

    // Obtain address and execute locator task
    String address = mSearchEditText.getText().toString();
    executeLocatorTask(address);
  }

  /**
   * Set up the search parameters and execute the Locator task.
   * 
   * @param address
   */
  private void executeLocatorTask(String address) {
    // Create Locator parameters from single line address string
    LocatorFindParameters findParams = new LocatorFindParameters(address);

    // Use the centre of the current map extent as the find location point
    findParams.setLocation(mMapView.getCenter(), mMapView.getSpatialReference());

    // Calculate distance for find operation
    Envelope mapExtent = new Envelope();
    mMapView.getExtent().queryEnvelope(mapExtent);
    // assume map is in metres, other units wont work
    // double current envelope
    double distance = (mapExtent != null && mapExtent.getWidth() > 0) ? mapExtent.getWidth() * 2 : 10000;

    findParams.setDistance(distance);
    findParams.setMaxLocations(2);

    // Set address spatial reference to match map
    findParams.setOutSR(mMapView.getSpatialReference());

    // Execute async task to find the address
    new LocatorAsyncTask().execute(findParams);
  }

  /**
   * Called by DirectionsDialogFragment when user presses Get Directions button.
   * 
   * @param startPoint String entered by user to define start point.
   * @param endPoint String entered by user to define end point.
   * @return true if routing task executed, false if parameters rejected. If this method rejects the parameters it must
   *         display an explanatory Toast to the user before returning.
   */
  @Override
  public boolean onGetDirections(String startPoint, String endPoint) {
    if (startPoint.equals(getString(R.string.my_location)) && mLocation == null) {
      Toast.makeText(MapsAppActivity.this, getString(R.string.need_location_fix), Toast.LENGTH_LONG).show();
      return false;
    }
    // remove any previous graphics and callouts
    mLocationLayer.removeAll(); // TODO: confirm if this makes sense
    // remove any previous routes
    mRouteLayer.removeAll();
    // set parameters to geocode address for points
    executeRoutingTask(startPoint, endPoint);
    return true;
  }

  /**
   * Set up Route Parameters to execute RouteTask
   * 
   * @param start
   * @param end
   */
  private void executeRoutingTask(String start, String end) {
    // create a list of start end point params
    LocatorFindParameters routeStartParams = new LocatorFindParameters(start);
    LocatorFindParameters routeEndParams = new LocatorFindParameters(end);
    List<LocatorFindParameters> routeParams = new ArrayList<LocatorFindParameters>();
    // add params to list
    routeParams.add(routeStartParams);
    routeParams.add(routeEndParams);
    // run asych route task
    new RouteAsyncTask().execute(routeParams);

  }

  /*
   * This class provides an AsyncTask that performs a geolocation request on a background thread and displays the first
   * result on the map on the UI thread.
   */
  private class LocatorAsyncTask extends AsyncTask<LocatorFindParameters, Void, List<LocatorGeocodeResult>> {
    private Exception mException;

    public LocatorAsyncTask() {
    }

    @Override
    protected void onPreExecute() {
      // Display progress dialog on UI thread
      mProgressDialog.setMessage(getString(R.string.address_search));
      mProgressDialog.show();
    }

    @Override
    protected List<LocatorGeocodeResult> doInBackground(LocatorFindParameters... params) {
      // Perform routing request on background thread
      mException = null;
      List<LocatorGeocodeResult> results = null;

      // Create locator using default online geocoding service and tell it to find the given address
      Locator locator = Locator.createOnlineLocator();
      try {
        results = locator.find(params[0]);
      } catch (Exception e) {
        mException = e;
      }
      return results;
    }

    @Override
    protected void onPostExecute(List<LocatorGeocodeResult> result) {
      // Display results on UI thread
      mProgressDialog.dismiss();
      if (mException != null) {
        Log.w(TAG, "LocatorSyncTask failed with:");
        mException.printStackTrace();
        Toast.makeText(MapsAppActivity.this, getString(R.string.addressSearchFailed), Toast.LENGTH_LONG).show();
        return;
      }

      if (result.size() == 0) {
        Toast.makeText(MapsAppActivity.this, getString(R.string.noResultsFound), Toast.LENGTH_LONG).show();
      } else {
        // Use first result in the list
        geocodeResult = result.get(0);

        // get return geometry from geocode result
        Geometry resultLocGeom = geocodeResult.getLocation();
        // create marker symbol to represent location TODO: need offset?
        SimpleMarkerSymbol resultSymbol = new SimpleMarkerSymbol(Color.RED, 16, SimpleMarkerSymbol.STYLE.CROSS);
        // create graphic object for resulting location
        Graphic resultLocGraphic = new Graphic(resultLocGeom, resultSymbol);
        // add graphic to location layer
        mLocationLayer.addGraphic(resultLocGraphic);
        // create text symbol for return address
        String address = geocodeResult.getAddress();
        TextSymbol resultAddress = new TextSymbol(20, address, Color.BLACK);
        // create offset for text
        resultAddress.setOffsetX(-4 * address.length()); // TODO: improve this rough calculation?
        resultAddress.setOffsetY(10);
        // create a graphic object for address text
        Graphic resultText = new Graphic(resultLocGeom, resultAddress);
        // add address text graphic to location graphics layer
        mLocationLayer.addGraphic(resultText);

        // Zoom map to geocode result location
        mMapView.zoomToResolution(geocodeResult.getLocation(), 2);
      }
    }

  }

  /**
   * This class provides an AsyncTask that performs a routing request on a background thread and displays the resultant
   * route on the map on the UI thread.
   */
  private class RouteAsyncTask extends AsyncTask<List<LocatorFindParameters>, Void, RouteResult> {
    private Exception mException;

    @Override
    protected void onPreExecute() {
      // Display progress dialog on UI thread
      mProgressDialog.setMessage(getString(R.string.route_search));
      mProgressDialog.setOnDismissListener(new OnDismissListener() {
        @Override
        public void onDismiss(DialogInterface arg0) {
          RouteAsyncTask.this.cancel(true);
        }
      });
      mProgressDialog.show();
    }

    @Override
    protected RouteResult doInBackground(List<LocatorFindParameters>... params) {
      // Perform routing request on background thread
      mException = null;

      // Define route objects
      List<LocatorGeocodeResult> geocodeStartResult = null;
      List<LocatorGeocodeResult> geocodeEndResult = null;
      Point startPoint = null;
      Point endPoint = null;
      RouteParameters routeParams = null;

      // Create a new locator to geocode start/end points
      Locator locator = Locator.createOnlineLocator();

      try {
        // Geocode start position, or use My Location (from GPS)
        LocatorFindParameters startParam = params[0].get(0);
        if (startParam.getText().equals(getString(R.string.my_location))) {
          startPoint = (Point) GeometryEngine.project(mLocation, wm, egs);
        } else {
          geocodeStartResult = locator.find(startParam);
          startPoint = geocodeStartResult.get(0).getLocation();
          if (isCancelled()) {
            return null;
          }
        }

        // Geocode the destination
        LocatorFindParameters endParam = params[0].get(1);
        geocodeEndResult = locator.find(endParam);
        endPoint = geocodeEndResult.get(0).getLocation();
      } catch (Exception e) {
        mException = e;
        return null;
      }
      if (isCancelled()) {
        return null;
      }

      // Create a new routing task pointing to an NAService
      try {
        routeTask = RouteTask.createOnlineRouteTask(getString(R.string.routingservice_url), null);
        // build routing parameters
        routeParams = routeTask.retrieveDefaultRouteTaskParameters();
      } catch (Exception e) {
        mException = e;
        return null;
      }
      if (isCancelled()) {
        return null;
      }

      // Setup route parameters
      NAFeaturesAsFeature routeFAF = new NAFeaturesAsFeature();
      StopGraphic sgStart = new StopGraphic(startPoint);
      StopGraphic sgEnd = new StopGraphic(endPoint);
      routeFAF.setFeatures(new Graphic[] { sgStart, sgEnd });
      routeFAF.setCompressedRequest(true);
      routeParams.setStops(routeFAF);
      routeParams.setOutSpatialReference(mMapView.getSpatialReference());

      // Solve the route
      try {
        routeResult = routeTask.solve(routeParams);
      } catch (Exception e) {
        mException = e;
        return null;
      }
      if (isCancelled()) {
        return null;
      }
      return routeResult;
    }

    @Override
    protected void onPostExecute(RouteResult result) {
      // Display results on UI thread
      mProgressDialog.dismiss();
      if (mException != null) {
        Log.w(TAG, "RouteSyncTask failed with:");
        mException.printStackTrace();
        Toast.makeText(MapsAppActivity.this, getString(R.string.routingFailed), Toast.LENGTH_LONG).show();
        return;
      }

      // Get first item in list of routes provided by server
      route = result.getRoutes().get(0);

      // Create polyline graphic of the full route
      SimpleLineSymbol lineSymbol = new SimpleLineSymbol(Color.RED, 2, STYLE.SOLID);
      Graphic routeGraphic = new Graphic(route.getRouteGraphic().getGeometry(), lineSymbol);

      // Create point graphic to mark end of route
      int endPointIndex = ((Polyline) routeGraphic.getGeometry()).getPointCount() - 1;
      Point point = ((Polyline) routeGraphic.getGeometry()).getPoint(endPointIndex);
      Graphic endGraphic = createMarkerGraphic(point);

      // route and end point graphics to route layer
      mRouteLayer.addGraphics(new Graphic[] { routeGraphic, endGraphic });

      // Zoom to the extent of the entire route with a padding
      mMapView.setExtent(route.getEnvelope(), 100);
    }

  }

  /**
   * This class provides an AsyncTask that performs a reverse geocoding request on a background thread and displays the
   * resultant point on the map on the UI thread.
   */
  public class ReverseGeocodingAsyncTask extends AsyncTask<Point, Void, LocatorReverseGeocodeResult> {
    private Exception mException;
    private Point mPoint;

    @Override
    protected void onPreExecute() {
      // Display progress dialog on UI thread
      mProgressDialog.setMessage(getString(R.string.reverse_geocoding));
      mProgressDialog.show();
    }

    @Override
    protected LocatorReverseGeocodeResult doInBackground(Point... params) {
      // Perform reverse geocoding request on background thread
      mException = null;
      LocatorReverseGeocodeResult result = null;
      mPoint = params[0];

      // Create locator using default online geocoding service and tell it to find the given point
      Locator locator = Locator.createOnlineLocator();
      try {
        // Our input and output spatial reference will be the same as the map
        SpatialReference mapRef = mMapView.getSpatialReference();
        result = locator.reverseGeocode(mPoint, 50.0, mapRef, mapRef);
      } catch (Exception e) {
        mException = e;
      }
      // return the resulting point(s)
      return result;
    }

    @Override
    protected void onPostExecute(LocatorReverseGeocodeResult result) {
      // Display results on UI thread
      mProgressDialog.dismiss();
      if (mException != null) {
        Log.w(TAG, "LocatorSyncTask failed with:");
        mException.printStackTrace();
        Toast.makeText(MapsAppActivity.this, getString(R.string.addressSearchFailed), Toast.LENGTH_LONG).show();
        return;
      }

      String resultAddress;

      // Construct a nicely formatted address from the results
      StringBuilder address = new StringBuilder();
      if (result != null && result.getAddressFields() != null) {
        Map<String, String> addressFields = result.getAddressFields();
        address.append(String.format("%s\n%s, %s %s", addressFields.get("Address"), addressFields.get("City"),
            addressFields.get("Region"), addressFields.get("Postal")));

        // Show the results of the reverse geocoding in a toast.
        resultAddress = address.toString();
        Toast.makeText(MapsAppActivity.this, resultAddress, Toast.LENGTH_LONG).show();

        // Draw marker on map.
        // create marker symbol to represent location TODO: need offset?
        SimpleMarkerSymbol symbol = new SimpleMarkerSymbol(Color.RED, 16, SimpleMarkerSymbol.STYLE.CROSS);
        mLocationLayer.addGraphic(new Graphic(mPoint, symbol));
      }
    }
  }

  Graphic createMarkerGraphic(Point point) {
    Drawable marker = getResources().getDrawable(R.drawable.marker);
    PictureMarkerSymbol destinationSymbol = new PictureMarkerSymbol(mMapView.getContext(), marker);
    // NOTE: marker's bounds not set till marker is used to create destinationSymbol
    float offsetY = convertPixelsToDp(MapsAppActivity.this, marker.getBounds().bottom);
    destinationSymbol.setOffsetY(offsetY);
    return new Graphic(point, destinationSymbol);
  }

  /**
   * Converts device specific pixels to density independent pixels.
   * 
   * @param context
   * @param px number of device specific pixels
   * @return number of density independent pixels
   */
  private float convertPixelsToDp(Context context, float px) {
    DisplayMetrics metrics = context.getResources().getDisplayMetrics();
    float dp = px / (metrics.densityDpi / 160f);
    return dp;
  }

}
