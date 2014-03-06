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

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.os.AsyncTask;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.esri.android.map.MapView;
import com.esri.android.mapsapp.R;
import com.esri.core.geometry.Polygon;
import com.esri.core.portal.BaseMap;
import com.esri.core.portal.Portal;
import com.esri.core.portal.WebMap;

public class BasemapsAdapter extends BaseAdapter {

  // need context to use it to construct view
  Context mContext;

  // hold onto a copy of all basemap items
  List<BasemapItem> items;

  Portal mPortal;

  Polygon mapExtent;

  // recreation web map
  WebMap recWebmap;

  // base webmap service
  WebMap baseWebmap = null;

  String basemapID;

  public BasemapsAdapter(Context c) {
    mContext = c;
  }

  public BasemapsAdapter(Context c, ArrayList<BasemapItem> portalItems, Polygon extent) {
    mContext = c;
    this.items = portalItems;
    mapExtent = extent;
  }

  @Override
  public void notifyDataSetChanged() {
    super.notifyDataSetChanged();
  }

  @Override
  public int getCount() {
    return items == null ? 0 : items.size();
  }

  @Override
  public Object getItem(int position) {
    return items.get(position);
  }

  @Override
  public long getItemId(int position) {
    return position;
  }

  @Override
  public View getView(final int position, View convertView, ViewGroup parent) {

    // Inflate view unless we have an old one to reuse
    View newView = convertView;
    if (convertView == null) {
      LayoutInflater inflator = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
      newView = inflator.inflate(R.layout.basemap_image, null);
    }

    // Create view for the thumbnail
    ImageView image = (ImageView) newView.findViewById(R.id.listImageView);
    image.setImageBitmap(items.get((position)).itemThumbnail);

    // Register listener for clicks on the thumbnail
    image.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(final View view) {
        new BasemapFetchAsyncTask().execute(Integer.valueOf(position));
      }
    });

    // Set the title and return the view we've created
    TextView text = (TextView) newView.findViewById(R.id.listTextView);
    text.setText(items.get((position)).item.getTitle());
    return newView;
  }

  /**
   * This class provides an AsyncTask that fetches the selected basemap on a background thread and ...
   */
  private class BasemapFetchAsyncTask extends AsyncTask<Integer, Void, BaseMap> {
    private Exception mException;

    ProgressDialog mProgressDialog;

    public BasemapFetchAsyncTask() {
      // Create and initialise the progress dialog
      mProgressDialog = new ProgressDialog(mContext) {
        @Override
        public void onBackPressed() {
          // Back key pressed - just dismiss the dialog
          mProgressDialog.dismiss();
        }
      };
      //mProgressDialog.setTitle(mContext.getString(R.string.app_name));
      mProgressDialog.setMessage(mContext.getString(R.string.fetching_selected_basemap));
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
        Toast.makeText(mContext, mContext.getString(R.string.basemapSearchFailed), Toast.LENGTH_LONG).show();
        return;
      }
      if (!isCancelled()) {
        // Success - create new MapView and pass it to MapsAppActivity to display
        MapView mapView = new MapView(mContext, recWebmap, selectedBasemap, null, null);
        MapsAppActivity activity = (MapsAppActivity) mContext;
        activity.setMapView(mapView);

        /*
        if (!updateMapView.isLoaded()) {
          // wait till map is loaded
          final Handler handler = new Handler();
          handler.postDelayed(new Runnable() {

            @Override
            public void run() {
              // honor the maps extent
              updateMapView.setExtent(mapExtent);

            }
          }, 250);

        } else {
          // honor the maps extent
          updateMapView.setExtent(mapExtent);
        }
        */
      }
    }

    private BaseMap fetchSelectedBasemap(int position) throws Exception {
      BaseMap selectedBasemap = null;
      String url = "http://www.arcgis.com";
      mPortal = new Portal(url, null);
      basemapID = items.get(position).item.getItemId();

      // recreation webmap item id to create a WebMap
      String itemID = mContext.getString(R.string.rec_webmap_id);
      // create recreation Webmap
      recWebmap = WebMap.newInstance(itemID, mPortal);
      // create a new WebMap of selected basemap from default portal
      baseWebmap = WebMap.newInstance(basemapID, mPortal);

      // Get the WebMaps basemap
      selectedBasemap = baseWebmap.getBaseMap();
      return selectedBasemap;
    }
  }

}
