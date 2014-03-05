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

import android.content.Context;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.esri.android.map.MapView;
import com.esri.android.mapsapp.R;
import com.esri.core.geometry.Polygon;
import com.esri.core.portal.BaseMap;
import com.esri.core.portal.Portal;
import com.esri.core.portal.WebMap;

public class BasemapsAdapter extends BaseAdapter {

  // need context to use it to construct view
  private Context mContext;

  // hold onto a copy of all basemap items
  private List<BasemapItem> items;

  MapView updateMapView;

  Portal mPortal;

  Polygon mapExtent;

  // recreation web map
  WebMap recWebmap;

  // base webmap service
  WebMap baseWebmap = null;

  // basemap selected from baseWebmap
  BaseMap selectedBasemap;

  String basemapID;

  public BasemapsAdapter(Context c) {
    mContext = c;
  }

  public BasemapsAdapter(Context c, ArrayList<BasemapItem> portalItems, MapView aMapView, Polygon extent) {
    mContext = c;
    this.items = portalItems;
    updateMapView = aMapView;
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
        //TODO replace this with an AsyncTask??
        new Thread(new Runnable() {

          @Override
          public void run() {
            handleClick(view, position);
          }

        }).start();

      }
    });

    // Set the title and return the view we've created
    TextView text = (TextView) newView.findViewById(R.id.listTextView);
    text.setText(items.get((position)).item.getTitle());
    return newView;
  }

  public void handleClick(View view, int position) {
    String url = "http://www.arcgis.com";
    mPortal = new Portal(url, null);
    basemapID = items.get(position).item.getItemId();

    try {
      // recreation webmap item id to create a @WebMap
      String itemID = mContext.getString(R.string.rec_webmap_id);
      // create recreation Webmap
      recWebmap = WebMap.newInstance(itemID, mPortal);
      // create a new WebMap of selected basemap from default
      // portal
      baseWebmap = WebMap.newInstance(basemapID, mPortal);

    } catch (Exception e) {
      e.printStackTrace();
    }

    view.post(new Runnable() {

      @Override
      public void run() {
        // Get the WebMaps basemap
        selectedBasemap = baseWebmap.getBaseMap();
        // switch basemaps on the recreation webmap
        updateMapView = new MapView(mContext, recWebmap, selectedBasemap, null, null);
        // reset the content view for the updated MapView
        MapsAppActivity basemapsActivity = (MapsAppActivity) mContext;
        basemapsActivity.setMapView(updateMapView);

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
      }
    });

  }

}
