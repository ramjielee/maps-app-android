package com.esri.android.mappsapp.basemaps;

import java.util.ArrayList;
import java.util.List;

import android.app.DialogFragment;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridView;
import android.widget.Toast;

import com.esri.android.map.MapView;
import com.esri.android.mappsapp.basemaps.BasemapsAdapter.BasemapsAdapterClickListener;
import com.esri.android.mapsapp.R;
import com.esri.core.portal.BaseMap;
import com.esri.core.portal.Portal;
import com.esri.core.portal.PortalGroup;
import com.esri.core.portal.PortalInfo;
import com.esri.core.portal.PortalItem;
import com.esri.core.portal.PortalItemType;
import com.esri.core.portal.PortalQueryParams;
import com.esri.core.portal.PortalQueryParams.PortalQuerySortOrder;
import com.esri.core.portal.PortalQueryResultSet;
import com.esri.core.portal.WebMap;

public class BasemapsDialogFragment extends DialogFragment implements BasemapsAdapterClickListener {

  private static final String TAG = "BasemapsDialogFragment";

  /**
   * A callback interface that all activities containing this fragment must implement, to receive a directions request
   * from this fragment.
   */
  public interface BasemapsDialogListener {
    /**
     * Callback for when a new basemap is selected.
     * 
     * @param mapView MapView object containing the new basemap.
     */
    public void onBasemapChanged(MapView mapView);
  }

  BasemapsDialogListener mBasemapsDialogListener;

  BasemapsAdapter mBasemapsAdapter;

  ArrayList<BasemapItem> mBasemapItemList;

  // Recreation WebMap, used to host the selected basemap layers
  WebMap mRecWebmap;

  ProgressDialog mProgressDialog;

  // Mandatory empty constructor for fragment manager to recreate fragment after it's destroyed.
  public BasemapsDialogFragment() {
  }

  /**
   * Sets listener for selection of new basemap.
   * 
   * @param listener
   */
  public void setBasemapsDialogListener(BasemapsDialogListener listener) {
    mBasemapsDialogListener = listener;
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setStyle(DialogFragment.STYLE_NORMAL, android.R.style.Theme_Holo_Light_DarkActionBar);

    // Create and initialise the progress dialog
    mProgressDialog = new ProgressDialog(getActivity()) {
      @Override
      public void onBackPressed() {
        // Back key pressed - just dismiss the dialog
        mProgressDialog.dismiss();
      }
    };
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    getDialog().setTitle(R.string.title_basemaps_dialog);

    // Inflate basemaps grid layout and setup list and adapter to back it
    GridView view = (GridView)inflater.inflate(R.layout.grid_layout, container, false);
    mBasemapItemList = new ArrayList<BasemapItem>();
    mBasemapsAdapter = new BasemapsAdapter(getActivity(), mBasemapItemList, this);
    view.setAdapter(mBasemapsAdapter);

    return view;
  }

  @Override
  public void onViewCreated(View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    
    //Moved this from onCreateView() in effort to get progress dialofg showing
    
    // Search for available basemaps and populate the grid with them
    new BasemapSearchAsyncTask().execute();

    mProgressDialog.setMessage(getString(R.string.fetching_basemaps));
    mProgressDialog.setOnDismissListener(new OnDismissListener() {
      @Override
      public void onDismiss(DialogInterface arg0) {
        //BasemapSearchAsyncTask.this.cancel(true);
      }
    });
    mProgressDialog.show();

  }

  @Override
  public void onBasemapItemClicked(int listPosition) {
    new BasemapFetchAsyncTask().execute(Integer.valueOf(listPosition));
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
        Log.w(TAG, "BasemapSearchAsyncTask failed with:");
        mException.printStackTrace();
        Toast.makeText(getActivity(), getString(R.string.basemapSearchFailed), Toast.LENGTH_LONG).show();
        dismiss();
        return;
      }
      if (isCancelled()) {
        dismiss();
      } else {
        // Success - update grid with results
        mBasemapsAdapter.notifyDataSetChanged();
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
      Portal portal = new Portal(url, null);

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
        PortalQueryResultSet<PortalItem> queryResultSet = portal.findItems(queryParams);
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

  /**
   * This class provides an AsyncTask that fetches the selected basemap on a background thread and ...
   */
  private class BasemapFetchAsyncTask extends AsyncTask<Integer, Void, BaseMap> {
    private Exception mException;

    public BasemapFetchAsyncTask() {
      mProgressDialog.setMessage(getString(R.string.fetching_selected_basemap));
      mProgressDialog.setOnDismissListener(new OnDismissListener() {
        @Override
        public void onDismiss(DialogInterface arg0) {
          BasemapFetchAsyncTask.this.cancel(true);
        }
      });
    }

    @Override
    protected void onPreExecute() {
      // Display progress dialog on UI thread
      mProgressDialog.show();
    }

    @Override
    protected BaseMap doInBackground(Integer... params) {
      // Fetch basemap data on background thread
      BaseMap baseMap = null;
      mException = null;
      try {
        baseMap = fetchSelectedBasemap(params[0].intValue());
      } catch (Exception e) {
        mException = e;
      }
      return baseMap;
    }

    @Override
    protected void onPostExecute(BaseMap selectedBasemap) {
      // Display results on UI thread
      mProgressDialog.dismiss();
      if (mException != null) {
        mException.printStackTrace();
        Toast.makeText(getActivity(), getString(R.string.basemapSearchFailed), Toast.LENGTH_LONG).show();
      } else {
        if (!isCancelled()) {
          // Success - create new MapView and pass it to MapsAppActivity to display
          MapView mapView = new MapView(getActivity(), mRecWebmap, selectedBasemap, null, null);
          mBasemapsDialogListener.onBasemapChanged(mapView);
        }
      }
      dismiss();
    }

    private BaseMap fetchSelectedBasemap(int position) throws Exception {
      BaseMap selectedBasemap = null;
      String url = "http://www.arcgis.com";
      Portal portal = new Portal(url, null);
      String basemapID = mBasemapItemList.get(position).item.getItemId();

      // recreation webmap item id to create a WebMap
      String itemID = getString(R.string.rec_webmap_id);
      // create recreation Webmap
      mRecWebmap = WebMap.newInstance(itemID, portal);
      // create a new WebMap of selected basemap from default portal
      WebMap baseWebmap = WebMap.newInstance(basemapID, portal);

      // Get the WebMaps basemap
      selectedBasemap = baseWebmap.getBaseMap();
      return selectedBasemap;
    }
  }
}
