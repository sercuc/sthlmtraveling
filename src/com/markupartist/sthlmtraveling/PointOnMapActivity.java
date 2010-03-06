package com.markupartist.sthlmtraveling;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Paint.Style;
import android.graphics.drawable.Drawable;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.widget.Toast;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
import com.google.android.maps.MyLocationOverlay;
import com.google.android.maps.Projection;
import com.markupartist.sthlmtraveling.graphics.LabelMarker;
import com.markupartist.sthlmtraveling.provider.planner.Stop;

import de.android1.overlaymanager.ManagedOverlay;
import de.android1.overlaymanager.ManagedOverlayGestureDetector;
import de.android1.overlaymanager.ManagedOverlayItem;
import de.android1.overlaymanager.OverlayManager;
import de.android1.overlaymanager.ZoomEvent;

public class PointOnMapActivity extends MapActivity {
    private static final String TAG = "PointOnMapActivity";

    public static String EXTRA_STOP = "com.markupartist.sthlmtraveling.pointonmap.stop";
    public static String EXTRA_HELP_TEXT = "com.markupartist.sthlmtraveling.pointonmap.helptext";

    private MapView mMapView;
    private MapController mapController;
    private GeoPoint mGeoPoint;
    private OverlayManager mOverlayManager;
    private ManagedOverlayItem mManagedOverlayItem;
    private Stop mStop;
    private MyLocationOverlay mMyLocationOverlay;
    private LabelMarker mLabelMarker;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.point_on_map);

        Bundle extras = getIntent().getExtras();
        mStop = (Stop) extras.getParcelable(EXTRA_STOP);
        String helpText = extras.getString(EXTRA_HELP_TEXT);

        showHelpToast(helpText);

        int textSize = 17;
        /*try {
            DisplayMetrics metrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(metrics);

            Field densityDpiFiled = DisplayMetrics.class.getField("densityDpi");
            int densityDpi = densityDpiFiled.getInt(null);
            if (densityDpi == DisplayMetrics.DENSITY_HIGH) {
                textSize = 22;
            }
        } catch (NoSuchFieldException e) {
            Log.d(TAG, "Older device...");
            ; // Just pass, we are dealing with an older device.
        } catch (IllegalArgumentException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        */

        mLabelMarker = new LabelMarker(getString(R.string.tap_to_select_this_point), textSize);

        mMapView = (MapView) findViewById(R.id.mapview);
        mMapView.setBuiltInZoomControls(true);
        mapController = mMapView.getController();

        // Use stops location if present, otherwise set a geo point in 
        // central Stockholm.
        if (mStop.getLocation() != null) {
            mGeoPoint = new GeoPoint(
                    (int) (mStop.getLocation().getLatitude() * 1E6), 
                    (int) (mStop.getLocation().getLongitude() * 1E6));
            mapController.setZoom(16);
        } else {
            mGeoPoint = new GeoPoint(
                    (int) (59.325309 * 1E6), 
                    (int) (18.069763 * 1E6));
            mapController.setZoom(12);
        }
        mapController.animateTo(mGeoPoint); 

        mOverlayManager = new OverlayManager(getApplication(), mMapView);

        myLocationOverlay();
        pointToSelectOverlay();
    }

    private void showHelpToast(String helpText) {
        if (helpText != null) {
            Toast.makeText(this, helpText, Toast.LENGTH_LONG).show();
        }
    }
    
    @Override
    public void onWindowFocusChanged(boolean b) {
        //pointToSelectOverlay();
        //myLocationOverlay();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.options_menu_point_on_map, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_my_location:
                if (mMyLocationOverlay != null) {
                    GeoPoint myLocation = mMyLocationOverlay.getMyLocation();
                    if (myLocation != null) {
                        mapController.animateTo(myLocation);
                    } else {
                        toastMissingMyLocationSource();
                    }
                } else {
                    toastMissingMyLocationSource();
                }
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void toastMissingMyLocationSource() {
        Toast.makeText(this, getText(R.string.my_location_source_disabled),
                Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
 
        if (mMyLocationOverlay != null) {
            mMyLocationOverlay.enableCompass();
            mMyLocationOverlay.enableMyLocation();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        disableMyLocation();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        disableMyLocation();
    }

    private void disableMyLocation() {
        if (mMyLocationOverlay != null) {
            mMyLocationOverlay.disableCompass();
            mMyLocationOverlay.disableMyLocation();
        }
    }
    
    @Override
    protected boolean isRouteDisplayed() {
        return false;
    }

    private void myLocationOverlay() {
        LocationManager locationManager =
            (LocationManager)getSystemService(LOCATION_SERVICE);
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            //mMyLocationOverlay = new MyLocationOverlay(this, mMapView);
            mMyLocationOverlay = new FixedMyLocationOverlay(this, mMapView);
            mMapView.getOverlays().add(mMyLocationOverlay);
            mMyLocationOverlay.enableCompass();
            mMyLocationOverlay.enableMyLocation();
        } else {
            Log.d(TAG, "No Location provider is not enabled, ignoring...");
        }
    }
    
    private void pointToSelectOverlay() {
        ManagedOverlay managedOverlay = mOverlayManager.createOverlay(
                mLabelMarker.getMarker());

        mManagedOverlayItem = new ManagedOverlayItem(mGeoPoint, "title", "snippet");
        managedOverlay.add(mManagedOverlayItem);

        managedOverlay.setOnOverlayGestureListener(
                new ManagedOverlayGestureDetector.OnOverlayGestureListener() {

            @Override
            public boolean onDoubleTap(MotionEvent motionEvent, ManagedOverlay managedOverlay,
                    GeoPoint geoPoint, ManagedOverlayItem managedOverlayItem) {
                return false;
            }

            @Override
            public void onLongPress(MotionEvent arg0, ManagedOverlay arg1) {
                // Needed by interface, not used
            }

            @Override
            public void onLongPressFinished(MotionEvent motionEvent,
                                            ManagedOverlay managedOverlay,
                                            GeoPoint geoPoint,
                                            ManagedOverlayItem managedOverlayItem) {
                // Needed by interface, not used
            }

            @Override
            public boolean onScrolled(MotionEvent arg0, MotionEvent arg1,
                    float arg2, float arg3, ManagedOverlay arg4) {
                return false;
            }

            @Override
            public boolean onSingleTap(MotionEvent motionEvent, 
                                       ManagedOverlay managedOverlay,
                                       GeoPoint geoPoint,
                                       ManagedOverlayItem managedOverlayItem) {
                if (managedOverlayItem != null) {
                    Toast.makeText(getApplicationContext(),
                            getText(R.string.point_selected), Toast.LENGTH_LONG).show();

                    GeoPoint currentPoint = managedOverlayItem.getPoint();

                    mStop.setLocation(currentPoint.getLatitudeE6(),
                            currentPoint.getLongitudeE6());
                    mStop.setName(getStopName(mStop.getLocation()));

                    setResult(RESULT_OK, (new Intent()).putExtra(EXTRA_STOP, mStop));
                    finish();
                } else {
                    managedOverlay.remove(mManagedOverlayItem);
                    mManagedOverlayItem = new ManagedOverlayItem(geoPoint, "", "");
                    managedOverlay.add(mManagedOverlayItem);

                    mapController.animateTo(geoPoint);    
                    mMapView.invalidate();
                }
                return true;
            }

            @Override
            public boolean onZoom(ZoomEvent zoomEvent, ManagedOverlay managedOverlay) {
                return false;
            }

        });
        mOverlayManager.populate();
    }

    private String getStopName(Location location) {
        Geocoder geocoder = new Geocoder(this);
        String name = "Unkown";
        try {
            List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
            if (addresses.size() > 0) {
                Address address = addresses.get(0);
                name = address.getThoroughfare();
            }
        } catch (IOException e) {
            Log.d(TAG, "Failed to get name " + e.getMessage());
        }
        return name;
    }

    /**
     * From the Android Developers group.
     * http://groups.google.com/group/android-developers/msg/6cb65da690614662
     * 
     * This class is a workaround for the MyLocationOverlay, that crashes on
     * some devices, in my case I got reports from a Spica device.
     */
    public class FixedMyLocationOverlay extends MyLocationOverlay {
        private boolean bugged = false;
        private Paint accuracyPaint;
        private Point center;
        private Point left;
        private Drawable drawable;
        private int width;
        private int height;

        public FixedMyLocationOverlay(Context context, MapView mapView) {
            super(context, mapView);
        }

        @Override
        protected void drawMyLocation(Canvas canvas, MapView mapView,
                Location lastFix, GeoPoint myLoc, long when) {
            if (!bugged) {
                try {
                    super.drawMyLocation(canvas, mapView, lastFix, myLoc, when);
                } catch (Exception e) {
                    bugged = true;
                }
            }
            if (bugged) {
                if (drawable == null) {
                    accuracyPaint = new Paint();
                    accuracyPaint.setAntiAlias(true);
                    accuracyPaint.setStrokeWidth(2.0f);
                    drawable = mapView.getContext().getResources().getDrawable(
                            R.drawable.my_location_bugged);
                    width = drawable.getIntrinsicWidth();
                    height = drawable.getIntrinsicHeight();
                    center = new Point();
                    left = new Point();
                }
                Projection projection = mapView.getProjection();
                double latitude = lastFix.getLatitude();
                double longitude = lastFix.getLongitude();
                float accuracy = lastFix.getAccuracy();
                float[] result = new float[1];
                Location.distanceBetween(latitude, longitude, latitude,
                        longitude + 1, result);
                float longitudeLineDistance = result[0];
                GeoPoint leftGeo = new GeoPoint(
                        (int) (latitude * 1e6),
                        (int) ((longitude - accuracy / longitudeLineDistance) * 1e6));
                projection.toPixels(leftGeo, left);
                projection.toPixels(myLoc, center);
                int radius = center.x - left.x;
                accuracyPaint.setColor(0xff6666ff);
                accuracyPaint.setStyle(Style.STROKE);
                canvas.drawCircle(center.x, center.y, radius, accuracyPaint);
                accuracyPaint.setColor(0x186666ff);
                accuracyPaint.setStyle(Style.FILL);
                canvas.drawCircle(center.x, center.y, radius, accuracyPaint);
                drawable.setBounds(center.x - width / 2, center.y - height / 2,
                        center.x + width / 2, center.y + height / 2);
                drawable.draw(canvas);
            }
        }
    }
}
