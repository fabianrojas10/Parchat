package com.example.parchat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
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
import com.example.parchat.databinding.ActivityDiaEventoBinding;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.maps.android.PolyUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DiaEvento extends FragmentActivity implements OnMapReadyCallback {
    //permisos y ids
    private static final String permisosMapa = Manifest.permission.ACCESS_FINE_LOCATION;
    public static final int SOLICITUD_GPS = 1;
    public static final int MAPAID = 2;

    //cuadros de texto
    private TextView busqueda;
    private TextView nombreEvento;
    private TextView fecha;
    private ImageButton miPosicion;
    private Evento ev;
    private double latitud;
    private double longitud;
    private boolean otroUsuario;
    private boolean presionado;
    private boolean presionadoEvento;
    private boolean presionadoPart;

    private GoogleMap mMap;
    private ActivityDiaEventoBinding binding;
    private Marker busquedaMarker;
    private Marker miUbicacion;
    private Marker partUbicacion;
    private ArrayList<LatLng> participantes;

    private LocationRequest locationRequest;
    private FusedLocationProviderClient mFusedLocationClient;
    private LocationCallback mLocationCallback;

    //sensor de luminosidad
    private SensorManager sensorManager;
    private Sensor lightSensor;
    private SensorEventListener lightSensorListener;

    private FirebaseAuth mAuth;
    private DatabaseReference myRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        locationRequest = createLocationRequest();
        latitud = 0;
        longitud = 0;
        otroUsuario = false;
        presionado = false;
        presionadoEvento = false;
        presionadoPart = false;
        binding = ActivityDiaEventoBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        busqueda = findViewById(R.id.barraBuscar);
        nombreEvento = findViewById(R.id.nEvento);
        fecha = findViewById(R.id.fecha);
        miPosicion = findViewById(R.id.miUbEventoHoy);
        ev = new Evento();
        participantes = new ArrayList<LatLng>();
        mLocationCallback = createLocationCallBack();
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        lightSensorListener = crearListener();

        mAuth = FirebaseAuth.getInstance();
        myRef = FirebaseDatabase.getInstance().getReference("eventos");

        revisarGPS();
        solicitarPermisos(this, permisosMapa, "Acceso a GPS");
    }

    @Override
    protected void onResume() {
        super.onResume();
        cargarDatos();
        iniciarActLocalizacion();
        cargarParticipantes();
    }



    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(lightSensorListener);
        detenerActLocalizacion();
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
        colocarPuntoReunion();
    }

    private void cargarDatos(){

        Bundle extras = getIntent().getExtras();
        ev = (Evento) extras.getSerializable("eventoH");
        busqueda.setText(ev.lugar);
        nombreEvento.setText(ev.nombreEvento);
        fecha.setText(ev.fecha);
        otroUsuario = extras.getBoolean("oUsuario");

    }

    public void colocarPuntoReunion(){
        LatLng posicion = new LatLng(ev.latitud,ev.longitud);
        busquedaMarker = mMap.addMarker(new MarkerOptions()
                .position(posicion).title(ev.lugar)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)));
        mMap.moveCamera(CameraUpdateFactory.newLatLng(posicion));
        mMap.moveCamera(CameraUpdateFactory.zoomTo(15));
    }

    //-----MiUbicacion-----------------------------------------------

    private LocationRequest createLocationRequest() {
        return LocationRequest.create()
                .setInterval(5000)
                .setFastestInterval(4000)
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    private LocationCallback createLocationCallBack() {
        return new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                if(locationResult.getLastLocation() != null){
                    Location locacion = locationResult.getLastLocation();
                    latitud = locacion.getLatitude();
                    longitud = locacion.getLongitude();

                    if(miUbicacion != null){
                        miUbicacion.remove();
                    }
                    LatLng miU = new LatLng(latitud,longitud);
                    miUbicacion = mMap.addMarker(new MarkerOptions().position(miU).title("mi ubicacion"));
                    if(presionado){
                        mMap.moveCamera(CameraUpdateFactory.newLatLng(miU));
                        mMap.moveCamera(CameraUpdateFactory.zoomTo(15));
                    }
                    pedirJSON();
                    actPosicion();
                }
            }
        };
    }

    public void irMiUbicacionH(View v){
        if(!presionado){
            presionado = true;
            binding.miUbEventoHoy.setBackgroundResource(R.drawable.btnubicacionp);
        }
        else{
            presionado = false;
            binding.miUbEventoHoy.setBackgroundResource(R.drawable.ubicacion);
        }
    }

    public void irUbicacionEvento(View v){
            mMap.moveCamera(CameraUpdateFactory.newLatLng( busquedaMarker.getPosition()));
            mMap.moveCamera(CameraUpdateFactory.zoomTo(15));
    }

    public void irUbicacionParticipante(View v){
        if(!presionadoPart){
            presionadoPart = true;
            binding.ubParticipante.setBackgroundResource(R.drawable.btnubicacionp);
        }
        else{
            presionadoPart = false;
            binding.ubParticipante.setBackgroundResource(R.drawable.ubicacion);
        }
    }

    private void actPosicion(){
        String miId = mAuth.getCurrentUser().getUid();
        if(otroUsuario){
            Log.i("org", "act posicion invitado");
            myRef = FirebaseDatabase.getInstance().getReference("eventos").child(ev.idorganizador)
                    .child(ev.id).child("participantes").child(miId);
        }
        else{
            myRef = FirebaseDatabase.getInstance().getReference("eventos/" + miId)
                    .child(ev.id).child("participantes").child(miId);
        }
        Posicion nPos = new Posicion();
        nPos.latitud = latitud;
        nPos.longitud = longitud;
        myRef.setValue(nPos);
    }

    private void cargarParticipantes() {
        String miId = mAuth.getCurrentUser().getUid();
        myRef = FirebaseDatabase.getInstance().getReference("eventos").child(ev.idorganizador)
                .child(ev.id);
        myRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Evento evActual = snapshot.getValue(Evento.class);

                participantes.clear();
                for (Map.Entry<String,Posicion> entry : evActual.participantes.entrySet()){
                    if(!entry.getKey().equals(miId)){

                        if(partUbicacion != null){
                            partUbicacion.remove();
                        }
                        LatLng partPos = new LatLng(entry.getValue().latitud,entry.getValue().longitud);
                        partUbicacion = mMap.addMarker(new MarkerOptions()
                                .position(partPos).title("participante")
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));
                        if(presionadoPart){
                            mMap.moveCamera(CameraUpdateFactory.newLatLng(partPos));
                            mMap.moveCamera(CameraUpdateFactory.zoomTo(15));
                        }
                    }
                }

            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }



    //-----sensor de luminosidad-------------------------
    private SensorEventListener crearListener() {
        return new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent sensorEvent) {
                if(mMap != null){
                    if(sensorEvent.values[0] < 10){
                        mMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(DiaEvento.this,R.raw.modo_oscuro));
                    }
                    else{
                        mMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(DiaEvento.this,R.raw.modo_claro));
                    }
                }
            }
            @Override
            public void onAccuracyChanged(Sensor sensor, int i) {}
        };
    }

    //-------------------acceder a la ubicacion--------------------------------

    private void solicitarPermisos(Activity crearEvento, String permisosMapa, String acceso) {
        if (ContextCompat.checkSelfPermission(crearEvento,permisosMapa) != PackageManager.PERMISSION_GRANTED){
            if(ActivityCompat.shouldShowRequestPermissionRationale(crearEvento,permisosMapa)){
                Toast.makeText(this, "No se podra mostrar su ubicación actual", Toast.LENGTH_SHORT).show();
            }
        }
        ActivityCompat.requestPermissions(crearEvento, new String[]{permisosMapa},MAPAID);
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
                            resolvable.startResolutionForResult(DiaEvento.this,SOLICITUD_GPS);
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

    //----Trazar ruta-------------------------------------------
    private void pedirJSON(){
        if(miUbicacion != null && busquedaMarker != null){
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
        else if(miUbicacion == null){
            Toast.makeText(this, "proporcione su ubicación",Toast.LENGTH_SHORT).show();
        }
        else if(busquedaMarker == null){
            Toast.makeText(this, "Indique el lugar del evento",Toast.LENGTH_SHORT).show();
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

                        mMap.addPolyline(new PolylineOptions().addAll(lista).color(Color.CYAN).width(6));
                    }
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}