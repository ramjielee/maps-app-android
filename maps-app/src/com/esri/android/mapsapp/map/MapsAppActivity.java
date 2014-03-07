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

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationListener;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.Toast;

import com.esri.android.map.GraphicsLayer;
import com.esri.android.map.LocationDisplayManager;
import com.esri.android.map.LocationDisplayManager.AutoPanMode;
import com.esri.android.map.MapView;
import com.esri.android.map.ags.ArcGISFeatureLayer;
import com.esri.android.map.event.OnLongPressListener;
import com.esri.android.map.event.OnStatusChangedListener;
import com.esri.android.map.popup.Popup;
import com.esri.android.mapsapp.R;
import com.esri.android.mapsapp.location.DirectionsDialogFragment;
import com.esri.android.mapsapp.location.DirectionsDialogFragment.DirectionsDialogListener;
import com.esri.android.mapsapp.location.ReverseGeocoding;
import com.esri.android.mapsapp.map.PopupFragment.OnEditListener;
import com.esri.core.geometry.Envelope;
import com.esri.core.geometry.Geometry;
import com.esri.core.geometry.GeometryEngine;
import com.esri.core.geometry.LinearUnit;
import com.esri.core.geometry.Point;
import com.esri.core.geometry.Polyline;
import com.esri.core.geometry.SpatialReference;
import com.esri.core.geometry.Unit;
import com.esri.core.map.CallbackListener;
import com.esri.core.map.FeatureEditResult;
import com.esri.core.map.Graphic;
import com.esri.core.portal.Portal;
import com.esri.core.portal.PortalGroup;
import com.esri.core.portal.PortalInfo;
import com.esri.core.portal.PortalItem;
import com.esri.core.portal.PortalItemType;
import com.esri.core.portal.PortalQueryParams;
import com.esri.core.portal.PortalQueryParams.PortalQuerySortOrder;
import com.esri.core.portal.PortalQueryResultSet;
import com.esri.core.symbol.PictureMarkerSymbol;
import com.esri.core.symbol.SimpleLineSymbol;
import com.esri.core.symbol.SimpleLineSymbol.STYLE;
import com.esri.core.symbol.SimpleMarkerSymbol;
import com.esri.core.symbol.TextSymbol;
import com.esri.core.tasks.geocode.Locator;
import com.esri.core.tasks.geocode.LocatorFindParameters;
import com.esri.core.tasks.geocode.LocatorGeocodeResult;
import com.esri.core.tasks.na.NAFeaturesAsFeature;
import com.esri.core.tasks.na.Route;
import com.esri.core.tasks.na.RouteParameters;
import com.esri.core.tasks.na.RouteResult;
import com.esri.core.tasks.na.RouteTask;
import com.esri.core.tasks.na.StopGraphic;

/**
 * Entry point into the Maps App.
 */
public class MapsAppActivity extends Activity implements OnEditListener, DirectionsDialogListener {

  private static final String TAG = "MapsAppActivity";

  MapView mMapView = null;

  // Recreation webmap URL
  String recWebMapURL;

  int basemap;

  // Geocoding definitions
  Locator locator;

  LocatorGeocodeResult geocodeResult;

  // graphics layer to show geocode result
  GraphicsLayer locationLayer;

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

  GridView mBasemapGridView;

  BasemapsAdapter mBasemapsAdapter;

  ArrayList<BasemapItem> mBasemapItemList;

  Portal portal;

  PortalQueryResultSet<PortalItem> queryResultSet;

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
  GraphicsLayer routeLayer;

  // bundle to get routing parameters back to UI
  Bundle extras;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    // Create MapView to show the recreation WebMap
    recWebMapURL = getString(R.string.rec_webmap_url);
    mMapView = new MapView(this, recWebMapURL, "", "");

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
  public void setMapView(MapView mapView) {
    String mapViewState = null;
    if (mapView == mMapView) {
      mapViewState = mMapView.retainState();
    } else {
      // Need this to ensure old MapView's resources are freed up and location tracking is disabled.
      // Maybe unnecessary after MapView implementation is fixed.
      mMapView.getLocationDisplayManager().stop();
      mMapView.recycle();
    }
    mMapView = mapView;
    mMapView.setOnSingleTapListener(new SingleTapListener(mMapView));
    // attribute ESRI logo to map
    mMapView.setEsriLogoVisible(true);
    // enable map to wrap around date line
    mMapView.enableWrapAround(true);
    mMapView.setId(R.id.map); // Used in onBackPressed()
    if (mapViewState != null) {
      mMapView.restoreState(mapViewState);
    }

    setContentView(mMapView);

    // Zoom to device location and accept intent from route layout
    mMapView.setOnStatusChangedListener(new OnStatusChangedListener() {

      private static final long serialVersionUID = 1L;

      @Override
      public void onStatusChanged(Object source, STATUS status) {
        if (source == mMapView && status == STATUS.INITIALIZED) {
          // add search and routing layers
          addGraphicLayers();
          // start location service
          LocationDisplayManager locDispMgr = mMapView.getLocationDisplayManager();
          locDispMgr.setAutoPanMode(AutoPanMode.OFF);
          locDispMgr.setAllowNetworkLocation(true); //TODO: why doesn't this seem to work?
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

        }
      }
    });

    mMapView.setOnLongPressListener(new OnLongPressListener() {

      private static final long serialVersionUID = 1L;

      @Override
      public boolean onLongPress(float x, float y) {
        Point mapPoint = mMapView.toMapPoint(x, y);
        new ReverseGeocoding(MapsAppActivity.this, mMapView).execute(mapPoint);
        return true;
      }
    });
  }

  private void addGraphicLayers() {
    // Add location layer
    locationLayer = new GraphicsLayer();
    mMapView.addLayer(locationLayer);

    // Add the route graphic layer (shows the full route)
    routeLayer = new GraphicsLayer();
    mMapView.addLayer(routeLayer);
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
        // This calls back to onGetDirections() to do the routing
        DirectionsDialogFragment frag = new DirectionsDialogFragment();
        frag.setDirectionsDialogListener(this);
        frag.show(getFragmentManager(), null);
        return true;

      case R.id.basemaps:
        // Inflate basemaps grid layout and setup list and adapter to back it
        LayoutInflater inflator = LayoutInflater.from(this);
        mBasemapGridView = (GridView) inflator.inflate(R.layout.grid_layout, null);
        mBasemapItemList = new ArrayList<BasemapItem>();
        mBasemapsAdapter = new BasemapsAdapter(this, mBasemapItemList, mMapView.getExtent());
        mBasemapGridView.setAdapter(mBasemapsAdapter);

        // Search for available basemaps and populate the grid with them
        new BasemapSearchAsyncTask().execute();
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
    mMapView.pause();
    mMapView.getLocationDisplayManager().stop();
  }

  @Override
  protected void onResume() {
    super.onResume();

    // Start the MapView and LocationDisplayManager running again
    mMapView.unpause();
    mMapView.getLocationDisplayManager().start();
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
    locationLayer.removeAll();
    routeLayer.removeAll();

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
    locationLayer.removeAll(); // TODO: confirm if this makes sense
    // remove any previous routes
    routeLayer.removeAll();
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

  @Override
  public void onDelete(ArcGISFeatureLayer fl, Popup popup) {
    // Commit deletion to server
    Graphic gr = (Graphic) popup.getFeature();
    if (gr == null)
      return;
    fl.applyEdits(null, new Graphic[] { gr }, null, new EditCallbackListener(this, fl, popup, true, "Deleting feature"));

    // Dismiss popup
    this.getFragmentManager().popBackStack();
  }

  @Override
  public void onEdit(ArcGISFeatureLayer fl, Popup popup) {
    // Set popup into editing mode
    popup.setEditMode(true);
    // refresh menu items
    this.invalidateOptionsMenu();
  }

  @Override
  public void onSave(ArcGISFeatureLayer fl, Popup popup) {
    // Commit edits to server
    Graphic gr = (Graphic) popup.getFeature();
    if (gr != null) {
      Map<String, Object> attributes = gr.getAttributes();
      Map<String, Object> updatedAttrs = popup.getUpdatedAttributes();
      for (Entry<String, Object> entry : updatedAttrs.entrySet()) {
        attributes.put(entry.getKey(), entry.getValue());
      }
      Graphic newgr = new Graphic(gr.getGeometry(), null, attributes);
      fl.applyEdits(null, null, new Graphic[] { newgr }, new EditCallbackListener(this, fl, popup, true,
          "Saving feature"));
    }

    // Dismiss popup
    this.getFragmentManager().popBackStack();
  }

  /*
   * This class provides an AsyncTask that performs a geolocation request on a background thread and displays the 
   * first result on the map on the UI thread.
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
      locator = Locator.createOnlineLocator();
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
        locationLayer.addGraphic(resultLocGraphic);
        // create text symbol for return address
        String address = geocodeResult.getAddress();
        TextSymbol resultAddress = new TextSymbol(20, address, Color.BLACK);
        // create offset for text
        resultAddress.setOffsetX(-4 * address.length()); // TODO: improve this rough calculation?
        resultAddress.setOffsetY(10);
        // create a graphic object for address text
        Graphic resultText = new Graphic(resultLocGeom, resultAddress);
        // add address text graphic to location graphics layer
        locationLayer.addGraphic(resultText);

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
      if (!isCancelled()) {
        /*
         * if (result == null) { // update UI with notice that no results were found Toast toast =
         * Toast.makeText(MapsAppActivity.this, "No result found.", Toast.LENGTH_LONG); toast.show(); } else {
         */

        // Get first item in list of routes provided by server
        route = result.getRoutes().get(0);

        // Create polyline graphic of the full route
        SimpleLineSymbol lineSymbol = new SimpleLineSymbol(Color.RED, 2, STYLE.SOLID);
        Graphic routeGraphic = new Graphic(route.getRouteGraphic().getGeometry(), lineSymbol);

        // Create point graphic to mark end of route
        Drawable marker = getResources().getDrawable(R.drawable.stat_finish);
        PictureMarkerSymbol destinationSymbol = new PictureMarkerSymbol(mMapView.getContext(), marker);
        // NOTE: marker's bounds not set till marker is used to create destinationSymbol
        float offsetY = convertPixelsToDp(MapsAppActivity.this, marker.getBounds().bottom);
        destinationSymbol.setOffsetY(offsetY);
        int endPointIndex = ((Polyline) routeGraphic.getGeometry()).getPointCount() - 1;
        Graphic endGraphic = new Graphic(((Polyline) routeGraphic.getGeometry()).getPoint(endPointIndex),
            destinationSymbol);

        // route and end point graphics to route layer
        routeLayer.addGraphics(new Graphic[] { routeGraphic, endGraphic });

        // Zoom to the extent of the entire route with a padding
        mMapView.setExtent(route.getEnvelope(), 100);
      }
    }

  }

  /**
   * Converts device specific pixels to density independent pixels.
   * 
   * @param context
   * @param px number of device specific pixels
   * @return number of density independent pixels
   */
  public static float convertPixelsToDp(Context context, float px) {
    DisplayMetrics metrics = context.getResources().getDisplayMetrics();
    float dp = px / (metrics.densityDpi / 160f);
    return dp;
  }

  /**
   * This class provides an AsyncTask that fetches info about available basemaps on a background thread and displays a
   * grid containing these on the UI thread.
   */
  private class BasemapSearchAsyncTask extends AsyncTask<Void, Void, Void> {
    private Exception mException;

    public BasemapSearchAsyncTask() {
    }

    @Override
    protected void onPreExecute() {
      // Display progress dialog on UI thread
      mProgressDialog.setMessage(getString(R.string.fetching_basemaps));
      mProgressDialog.setOnDismissListener(new OnDismissListener() {
        @Override
        public void onDismiss(DialogInterface arg0) {
          BasemapSearchAsyncTask.this.cancel(true);
        }
      });
      mProgressDialog.show();
    }

    @Override
    protected Void doInBackground(Void... params) {
      // Fetch basemaps on background thread
      mException = null;
      try {
        fetchBasemapItems();
      } catch (Exception e) {
        mException = e;
      }
      return null;
    }

    @Override
    protected void onPostExecute(Void result) {
      // Display results on UI thread
      mProgressDialog.dismiss();
      if (mException != null) {
        Log.w(TAG, "RouteSyncTask failed with:");
        mException.printStackTrace();
        Toast.makeText(MapsAppActivity.this, getString(R.string.basemapSearchFailed), Toast.LENGTH_LONG).show();
        return;
      }
      if (!isCancelled()) {
        // Success - display grid containing results
        mBasemapsAdapter.notifyDataSetChanged();
        setContentView(mBasemapGridView);
      }
    }

    /**
     * Connects to portal and fetches info about basemaps.
     * 
     * @throws Exception
     */
    private void fetchBasemapItems() throws Exception {
      // Create a Portal object
      String url = "http://www.arcgis.com";
      portal = new Portal(url, null);

      // Fetch portal info from server
      PortalInfo portalInfo = portal.fetchPortalInfo();
      if (isCancelled()) {
        return;
      }

      // Get query to determine which basemap gallery group to us and create a PortalQueryParams from it
      String basemapGalleryGroupQuery = portalInfo.getBasemapGalleryGroupQuery();
      PortalQueryParams portalQueryParams = new PortalQueryParams(basemapGalleryGroupQuery);
      portalQueryParams.setCanSearchPublic(true);

      // Find groups that match the query
      PortalQueryResultSet<PortalGroup> results = portal.findGroups(portalQueryParams);
      if (isCancelled()) {
        return;
      }

      // Check we have found at least one basemap group
      List<PortalGroup> groupResults = results.getResults();
      if (groupResults.size() <= 0) {
        Log.i(TAG, "portal group empty");
      } else {
        // Create a PortalQueryParams to query for items in basemap group
        PortalQueryParams queryParams = new PortalQueryParams();
        queryParams.setCanSearchPublic(true);
        queryParams.setLimit(15);

        // Set query to search for WebMaps in only the first group we found
        String groupID = groupResults.get(0).getGroupId();
        queryParams.setQuery(PortalItemType.WEBMAP, groupID, null);
        queryParams.setSortField("name").setSortOrder(PortalQuerySortOrder.ASCENDING);

        // Find items that match the query
        queryResultSet = portal.findItems(queryParams);
        if (isCancelled()) {
          return;
        }

        // Loop through query results
        for (PortalItem item : queryResultSet.getResults()) {
          // Fetch item thumbnail from server
          byte[] data = item.fetchThumbnail();
          if (isCancelled()) {
            return;
          }
          if (data != null) {
            // Decode thumbnail and add this item to list for display
            Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
            BasemapItem portalItemData = new BasemapItem(item, bitmap);
            Log.i(TAG, "Item id = " + item.getTitle());
            mBasemapItemList.add(portalItemData);
          }
        }
      }
    }
  }

  // Handle callback on committing edits to server
  private class EditCallbackListener implements CallbackListener<FeatureEditResult[][]> {
    private String operation = "Operation ";

    private ArcGISFeatureLayer featureLayer = null;

    private boolean existingFeature = true;

    private Popup popup = null;

    private Context context;

    public EditCallbackListener(Context context, ArcGISFeatureLayer featureLayer, Popup popup, boolean existingFeature,
        String msg) {
      this.operation = msg;
      this.featureLayer = featureLayer;
      this.existingFeature = existingFeature;
      this.popup = popup;
      this.context = context;
    }

    @Override
    public void onCallback(FeatureEditResult[][] objs) {
      if (featureLayer == null || !featureLayer.isInitialized() || !featureLayer.isEditable())
        return;

      runOnUiThread(new Runnable() {

        @Override
        public void run() {
          Toast.makeText(context, operation + " succeeded!", Toast.LENGTH_SHORT).show();
        }
      });

      if (objs[1] == null || objs[1].length <= 0) {
        // Save attachments to the server if newly added attachments
        // exist.
        // Retrieve object id of the feature
        int oid;
        if (existingFeature) {
          oid = objs[2][0].getObjectId();
        } else {
          oid = objs[0][0].getObjectId();
        }
        // Get newly added attachments
        List<File> attachments = popup.getAddedAttachments();
        if (attachments != null && attachments.size() > 0) {
          for (File attachment : attachments) {
            // Save newly added attachment based on the object id of
            // the feature.
            featureLayer.addAttachment(oid, attachment, new CallbackListener<FeatureEditResult>() {
              @Override
              public void onError(Throwable e) {
                // Failed to save new attachments.
                runOnUiThread(new Runnable() {
                  @Override
                  public void run() {
                    Toast.makeText(context, "Adding attachment failed!", Toast.LENGTH_SHORT).show();
                  }
                });
              }

              @Override
              public void onCallback(FeatureEditResult arg0) {
                // New attachments have been saved.
                runOnUiThread(new Runnable() {
                  @Override
                  public void run() {
                    Toast.makeText(context, "Adding attachment succeeded!.", Toast.LENGTH_SHORT).show();
                  }
                });
              }
            });
          }
        }

        // Delete attachments if some attachments have been mark as
        // delete.
        // Get ids of attachments which are marked as delete.
        List<Integer> attachmentIDs = popup.getDeletedAttachmentIDs();
        if (attachmentIDs != null && attachmentIDs.size() > 0) {
          int[] ids = new int[attachmentIDs.size()];
          for (int i = 0; i < attachmentIDs.size(); i++) {
            ids[i] = attachmentIDs.get(i);
          }
          // Delete attachments
          featureLayer.deleteAttachments(oid, ids, new CallbackListener<FeatureEditResult[]>() {
            @Override
            public void onError(Throwable e) {
              // Failed to delete attachments
              runOnUiThread(new Runnable() {
                @Override
                public void run() {
                  Toast.makeText(context, "Deleting attachment failed!", Toast.LENGTH_SHORT).show();
                }
              });
            }

            @Override
            public void onCallback(FeatureEditResult[] objs) {
              // Attachments have been removed.
              runOnUiThread(new Runnable() {
                @Override
                public void run() {
                  Toast.makeText(context, "Deleting attachment succeeded!", Toast.LENGTH_SHORT).show();
                }
              });
            }
          });
        }

      }
    }

    @Override
    public void onError(Throwable e) {
      runOnUiThread(new Runnable() {

        @Override
        public void run() {
          Toast.makeText(context, operation + " failed!", Toast.LENGTH_SHORT).show();
        }
      });
    }

  }
}
