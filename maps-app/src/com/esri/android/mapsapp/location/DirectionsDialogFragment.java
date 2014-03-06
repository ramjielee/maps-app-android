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

package com.esri.android.mapsapp.location;

import android.app.DialogFragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import com.esri.android.mapsapp.R;

public class DirectionsDialogFragment extends DialogFragment {

  EditText mStartText;

  EditText mEndText;

  DirectionsDialogListener mDirectionsDialogListener;

  /**
   * A callback interface that all activities containing this fragment must implement, to receive a directions request
   * from this fragment.
   */
  public interface DirectionsDialogListener {
    /**
     * Callback for when the Get Directions button is pressed.
     * 
     * @param startPoint String entered by user to define start point.
     * @param endPoint String entered by user to define end point.
     */
    public void onGetDirections(String startPoint, String endPoint);
  }

  // Mandatory empty constructor for fragment manager to recreate fragment after it's destroyed.
  public DirectionsDialogFragment() {
  }

  /**
   * Sets listener for click on Get Directions button.
   * 
   * @param listener
   */
  public void setDirectionsDialogListener(DirectionsDialogListener listener) {
    mDirectionsDialogListener = listener;
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setStyle(DialogFragment.STYLE_NORMAL, 0);
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.directions_layout, container, false);
    getDialog().setTitle(R.string.title_directions_dialog);
    mStartText = (EditText) view.findViewById(R.id.myLocation);
    mEndText = (EditText) view.findViewById(R.id.endPoint);
    Button button = (Button) view.findViewById(R.id.getDirectionsButton);
    button.setOnClickListener(new View.OnClickListener() {

      @Override
      public void onClick(View v) {
        String startPoint = mStartText.getText().toString();
        String endPoint = mEndText.getText().toString();
        mDirectionsDialogListener.onGetDirections(startPoint, endPoint);
        dismiss();
      }

    });
    return view;
  }

}
