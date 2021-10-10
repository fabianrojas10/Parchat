package com.example.parchat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.example.parchat.databinding.ActivityCrearEventoBinding;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.maps.android.PolyUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Calendar;
import java.util.List;

public class CrearEvento extends FragmentActivity implements OnMapReadyCallback {
    //permisos y ids
    private String permisosMapa = Manifest.permission.ACCESS_FINE_LOCATION;
    public static final int SOLICITUD_GPS = 1;
    public static final int MAPAID = 2;

    //cuadros de texto
    private EditText busqueda;
    private EditText nombreEvento;
    private EditText fecha;
    private Button publicar;

    //fecha
    private DatePickerDialog.OnDateSetListener mDateSetListener;

    //mapa y localizacion
    private GoogleMap mMap;
    private ActivityCrearEventoBinding binding;
    private Marker miUbicacion;
    private Marker busquedaMarker;
    private double latitud;
    private double longitud;
    private LocationRequest locationRequest;
    private FusedLocationProviderClient mFusedLocationClient;
    private LocationCallback mLocationCallback;

    //sensor de luminosidad
    private SensorManager sensorManager;
    private Sensor lightSensor;
    private SensorEventListener lightSensorListener;

    //busqueda de localizaciones
    private Geocoder mgeocoder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        locationRequest = createLocationRequest();
        latitud = 0;
        longitud = 0;

        binding = ActivityCrearEventoBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        busqueda = findViewById(R.id.barraBuscar);
        nombreEvento = findViewById(R.id.nEvento);
        publicar = findViewById(R.id.publicar);
        fecha = findViewById(R.id.fecha);
        findViewById(R.id.fecha).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.i("fechas", "onClick: aparezco cuando hacen click");
                mostrarDatePicker();
            }
        });

        revisarGPS();
        solicitarPermisos(this, permisosMapa, "acceso a su GPS", MAPAID);

        mLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                if(locationResult.getLastLocation() == null){
                    return;
                }
                else{
                    Location locacion = locationResult.getLastLocation();
                    latitud = locacion.getLatitude();
                    longitud = locacion.getLongitude();
                }
            }
        };
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        lightSensorListener = crearListener();

        mgeocoder = new Geocoder(getBaseContext());
    }

    @Override
    protected void onResume() {
        super.onResume();
        iniciarActLocalizacion();
        sensorManager.registerListener(lightSensorListener,lightSensor,SensorManager.SENSOR_DELAY_NORMAL);

        //responder a las consultas de los lugares
        busqueda.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                if(i == EditorInfo.IME_ACTION_SEND){
                    String direccion = busqueda.getText().toString();
                    if(!direccion.isEmpty()){
                        try {
                            List<Address> direcciones = mgeocoder.getFromLocationName(direccion,5);
                            if(direcciones != null && !direcciones.isEmpty()){
                                Address resultado = direcciones.get(0);
                                LatLng posicion = new LatLng(resultado.getLatitude(),resultado.getLongitude());
                                if(mMap != null){
                                    if(busquedaMarker != null){
                                        busquedaMarker.remove();
                                    }
                                    busquedaMarker = mMap.addMarker(new MarkerOptions()
                                            .position(posicion).title(direccion)
                                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)));
                                    mMap.moveCamera(CameraUpdateFactory.newLatLng(posicion));
                                    mMap.moveCamera(CameraUpdateFactory.zoomTo(15));
                                }
                            }else{Toast.makeText(CrearEvento.this, "Direccion no encontrada",Toast.LENGTH_SHORT).show();}
                        } catch (IOException e) {e.printStackTrace();}
                    }
                    else{Toast.makeText(CrearEvento.this, "No hay dirección",Toast.LENGTH_SHORT).show();}
                }
                pedirJSON();
                return false;
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        detenerActLocalizacion();
        sensorManager.unregisterListener(lightSensorListener);
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setZoomGesturesEnabled(true);
    }

    //------mostrar la ubicacion del usuario al presionar el boton--------
    public void irMiUbicacion(View v){
        if(latitud != 0 && longitud != 0){
            LatLng miU = new LatLng(latitud,longitud);
            miUbicacion = mMap.addMarker(new MarkerOptions().position(miU).title("mi ubicacion"));
            mMap.moveCamera(CameraUpdateFactory.newLatLng(miU));
            mMap.moveCamera(CameraUpdateFactory.zoomTo(15));
        }
    }

    private LocationRequest createLocationRequest() {
        LocationRequest locationRequest = LocationRequest.create()
                .setInterval(10000)
                .setFastestInterval(5000)
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        return locationRequest;
    }
    //-------------------fechas------------------------------------------------

    private void mostrarDatePicker(){
        Calendar calendar = Calendar.getInstance();
        int anio = calendar.get(Calendar.YEAR);
        int mes = calendar.get(Calendar.MONTH);
        int dia = calendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog dialog = new DatePickerDialog(this, mDateSetListener,anio,mes,dia);
        dialog.show();

        mDateSetListener = new DatePickerDialog.OnDateSetListener() {
            @Override
            public void onDateSet(DatePicker datePicker, int anio, int mes, int dia) {
                mes = mes + 1;
                String fechaSeleccionada = dia + "/" + mes + "/" + anio;
                fecha.setText(fechaSeleccionada);
            }
        };

    }
    //-------------------Publicar----------------------------------------------

    public void publicar(View v){

    }
    //-------------------acceder a la ubicacion--------------------------------

    private void solicitarPermisos(Activity crearEvento, String permisosMapa, String acceso, int mapaid) {
        if (ContextCompat.checkSelfPermission(crearEvento,permisosMapa) != PackageManager.PERMISSION_GRANTED){
            if(ActivityCompat.shouldShowRequestPermissionRationale(crearEvento,permisosMapa)){}
        }
        ActivityCompat.requestPermissions(crearEvento, new String[]{permisosMapa},mapaid);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MAPAID){
            SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
            mapFragment.getMapAsync(this);
        }
    }

    //---------------------Permisos de GPS--------------------------------------

    private void revisarGPS(){
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder().addLocationRequest(locationRequest);
        SettingsClient client = LocationServices.getSettingsClient(this);
        Task<LocationSettingsResponse> task = client.checkLocationSettings(builder.build());
        task.addOnSuccessListener(this, new OnSuccessListener<LocationSettingsResponse>() {
            @Override
            public void onSuccess(LocationSettingsResponse locationSettingsResponse) {
                iniciarActLocalizacion();
            }
        });
        task.addOnFailureListener(this, new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                int codigostatus = ((ApiException)e).getStatusCode();
                switch (codigostatus){
                    case CommonStatusCodes.RESOLUTION_REQUIRED:
                        try {
                            ResolvableApiException resolvable = (ResolvableApiException) e;
                            resolvable.startResolutionForResult(CrearEvento.this,SOLICITUD_GPS);
                        } catch (IntentSender.SendIntentException sendIntentException) {

                        }break;
                    case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                        break;
                }
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == SOLICITUD_GPS){
            if (resultCode == RESULT_OK) {
                iniciarActLocalizacion();
            }else {
                Toast.makeText(this, "no podre mostrar su ubicación actual", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void iniciarActLocalizacion() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mFusedLocationClient.requestLocationUpdates(locationRequest, mLocationCallback, null);
        }
    }

    private void detenerActLocalizacion(){
        mFusedLocationClient.removeLocationUpdates(mLocationCallback);
    }
    //-----sensor de luminosidad-------------------------
    private SensorEventListener crearListener() {
        SensorEventListener sensor = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent sensorEvent) {
                if(mMap != null){
                    if(sensorEvent.values[0] < 10){
                        mMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(CrearEvento.this,R.raw.modo_oscuro));
                    }
                    else{
                        mMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(CrearEvento.this,R.raw.modo_claro));
                    }
                }
            }
            @Override
            public void onAccuracyChanged(Sensor sensor, int i) {

            }
        };
        return sensor;
    }

    //----Trazar ruta-------------------------------------------
    private void pedirJSON(){
        if(miUbicacion.getPosition() != null && busquedaMarker.getPosition() != null){
            String url = "https://maps.googleapis.com/maps/api/directions/json?origin="
                    + miUbicacion.getPosition().latitude
                    +"," + miUbicacion.getPosition().longitude
                    + "&destination=" +busquedaMarker.getPosition().latitude
                    +"," +busquedaMarker.getPosition().longitude
                    + "&key=AIzaSyCgbpdjKWsf7U4q2dkX4-PdFE49LKnIIiI";
            RequestQueue queue = Volley.newRequestQueue(getBaseContext());
            StringRequest request = new StringRequest(Request.Method.GET, url, new Response.Listener<String>() {
                @Override
                public void onResponse(String response) {
                    try {
                        JSONObject json = new JSONObject(response);

                        Log.i("ruta ", response);
                        Log.i("url", url);
                        trazarRuta(json);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {}
            });
            queue.add(request);
        }
        else if(miUbicacion.getPosition() == null){
            Toast.makeText(this, "proporcione su ubicación",Toast.LENGTH_SHORT).show();
        }
        else if(busquedaMarker.getPosition() == null){

        }
    }

    //https://maps.googleapis.com/maps/api/directions/json?origin=parametroLatitud,parametroLongitud&destination=parametroLatitud,parametroLongitud&key=AIzaSyCgbpdjKWsf7U4q2dkX4-PdFE49LKnIIiI
    private void trazarRuta(JSONObject ruta){
        JSONArray jroutes;
        JSONArray jlegs;
        JSONArray jsteps;

        try {
            jroutes = ruta.getJSONArray("routes");
            for(int i = 0; i < jroutes.length();i++){
                jlegs = ((JSONObject)(jroutes.get(i))).getJSONArray("legs");
                for(int j = 0; j < jlegs.length();j++){
                    jsteps = ((JSONObject)(jlegs.get(j))).getJSONArray("steps");
                    for(int k = 0; k < jsteps.length();k++){
                        String polyline = "" + ((JSONObject)((JSONObject)jsteps.get(k)).get("polyline")).get("points");
                        List<LatLng> lista = PolyUtil.decode(polyline);
                        mMap.addPolyline(new PolylineOptions().addAll(lista).color(Color.GRAY).width(5));
                    }
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}