package com.example.climaactivity

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    // Propiedades públicas para almacenar los datos del clima actual
    var ubicacion: String = ""
    var temperatura: Double = 0.0
    var humedad: Int = 0
    var aparenteTemperatura: Double = 0.0
    var precipitacion: Double = 0.0
    var nubosidad: Int = 0
    var velocidadViento: Double = 0.0

    // Variables públicas para almacenar datos del backend (hourly)
    var temperaturas: MutableList<Double> = mutableListOf()
    var humedades: MutableList<Int> = mutableListOf()
    var probabilidadesPrecipitacion: MutableList<Int> = mutableListOf()
    var precipitaciones: MutableList<Double> = mutableListOf()
    var evapotranspiraciones: MutableList<Double> = mutableListOf()
    var velocidadesViento: MutableList<Double> = mutableListOf()
    var humedadesSuelo: MutableList<Double> = mutableListOf()

    private lateinit var ubicacionTextView: TextView
    private lateinit var temperaturaTextView: TextView
    private lateinit var humedadTextView: TextView
    private lateinit var precipitacionTextView: TextView
    private lateinit var velocidadVientoTextView: TextView
    private lateinit var coberturaNubesTextView: TextView
    private lateinit var map: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Inicializar el fragmento del mapa
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Inicializar los TextViews
        ubicacionTextView = findViewById(R.id.locationTextView)
        temperaturaTextView = findViewById(R.id.temperatureTextView)
        humedadTextView = findViewById(R.id.humidityTextView)
        precipitacionTextView = findViewById(R.id.precipitationTextView)
        velocidadVientoTextView = findViewById(R.id.windSpeedTextView)
        coberturaNubesTextView = findViewById(R.id.cloudCoverTextView)

        // Inicializar FusedLocationProviderClient
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Obtener y mostrar los datos del clima
        obtenerDatosClima()

        // Ajustar los insets para la vista principal
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { vista, insets ->
            val barrasSistema = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            vista.setPadding(barrasSistema.left, barrasSistema.top, barrasSistema.right, barrasSistema.bottom)
            insets
        }
    }

    private fun obtenerDatosClima() {
        CoroutineScope(Dispatchers.Main).launch {
            // Obtener la ubicación actual
            obtenerUbicacionActual()
        }
    }

    private suspend fun obtenerUbicacionActual() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                val ubicacionActual = LatLng(it.latitude, it.longitude)
                // Actualizar el mapa con la ubicación en vivo
                map.addMarker(MarkerOptions().position(ubicacionActual).title("Mi Ubicación"))
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(ubicacionActual, 15f))

                // Llamar a la API del clima con la ubicación actual
                CoroutineScope(Dispatchers.IO).launch {
                    fetchDatosOpenMeteo(it.latitude, it.longitude)
                    withContext(Dispatchers.Main) {
                        actualizarInterfaz()
                    }
                }
            }
        }
    }

    private suspend fun fetchDatosOpenMeteo(latitud: Double, longitud: Double) {
        val cliente = OkHttpClient()

        val url = "https://api.open-meteo.com/v1/forecast?latitude=$latitud&longitude=$longitud" +
                "&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation,rain,cloud_cover,wind_speed_10m" +
                "&hourly=temperature_2m,relative_humidity_2m,precipitation_probability,precipitation,evapotranspiration,wind_speed_10m,soil_moisture_0_to_1cm" +
                "&forecast_days=1&models=best_match"

        val solicitud = Request.Builder()
            .url(url)
            .get()
            .addHeader("accept", "application/json")
            .build()

        try {
            val respuesta = cliente.newCall(solicitud).execute()
            if (respuesta.isSuccessful) {
                respuesta.body?.string()?.let { cuerpoRespuesta ->
                    val json = JSONObject(cuerpoRespuesta)
                    val currentData = json.getJSONObject("current")
                    val hourlyData = json.getJSONObject("hourly")

                    // Asignar valores a las propiedades públicas para los datos actuales
                    temperatura = currentData.getDouble("temperature_2m")
                    humedad = currentData.getInt("relative_humidity_2m")
                    aparenteTemperatura = currentData.getDouble("apparent_temperature")
                    precipitacion = currentData.getDouble("precipitation")
                    nubosidad = currentData.getInt("cloud_cover")
                    velocidadViento = currentData.getDouble("wind_speed_10m")

                    // Asignar valores a las variables públicas para los datos horarios
                    temperaturas = jsonArrayToDoubleList(hourlyData.getJSONArray("temperature_2m"))
                    humedades = jsonArrayToIntList(hourlyData.getJSONArray("relative_humidity_2m"))
                    probabilidadesPrecipitacion = jsonArrayToIntList(hourlyData.getJSONArray("precipitation_probability"))
                    precipitaciones = jsonArrayToDoubleList(hourlyData.getJSONArray("precipitation"))
                    evapotranspiraciones = jsonArrayToDoubleList(hourlyData.getJSONArray("evapotranspiration"))
                    velocidadesViento = jsonArrayToDoubleList(hourlyData.getJSONArray("wind_speed_10m"))
                    humedadesSuelo = jsonArrayToDoubleList(hourlyData.getJSONArray("soil_moisture_0_to_1cm"))
                }
            }
        } catch (e: IOException) {
            // Manejar excepción de entrada/salida
            e.printStackTrace()
        } catch (e: JSONException) {
            // Manejar excepción de JSON
            e.printStackTrace()
        }
    }

    // Funciones auxiliares para convertir JSONArray a List
    private fun jsonArrayToDoubleList(jsonArray: JSONArray): MutableList<Double> {
        val list = mutableListOf<Double>()
        for (i in 0 until jsonArray.length()) {
            list.add(jsonArray.getDouble(i))
        }
        return list
    }

    private fun jsonArrayToIntList(jsonArray: JSONArray): MutableList<Int> {
        val list = mutableListOf<Int>()
        for (i in 0 until jsonArray.length()) {
            list.add(jsonArray.getInt(i))
        }
        return list
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        // Esperar a que se obtenga la ubicación actual antes de mover la cámara
    }

    private fun actualizarInterfaz() {
        ubicacionTextView.text = "Ubicación: $ubicacion"
        temperaturaTextView.text = "Temperatura: $temperatura°C"
        humedadTextView.text = "Humedad: $humedad%"
        precipitacionTextView.text = "Precipitación: $precipitacion mm"
        velocidadVientoTextView.text = "Velocidad del Viento: $velocidadViento km/h"
        coberturaNubesTextView.text = "Nubosidad: $nubosidad%"

        println("-------------------------------------------------------------------------------------------------------------------------------------.")



        if (temperaturas.isNotEmpty() && humedades.isNotEmpty() && probabilidadesPrecipitacion.isNotEmpty()
            && precipitaciones.isNotEmpty() && evapotranspiraciones.isNotEmpty()
            && velocidadesViento.isNotEmpty() && humedadesSuelo.isNotEmpty()
            ) {

            println("Todos los arreglos están llenos.")
            for (temperatura in temperaturas) {
                println("Temperatura: $temperatura")
            }
            for (h in humedades) {
                println("humedade: $h")
            }
            for (p in probabilidadesPrecipitacion) {
                println("probabilidadesPrecipitacion: $p")
            }

            for (pre in precipitaciones) {
                println("precipitaciones: $pre")
            }
            for (e in evapotranspiraciones) {
                println("evapotranspiraciones: $e")
            }
            for (v in velocidadesViento) {
                println("velocidadesViento: $v")
            }
            for (h2 in humedadesSuelo) {
                println("humedadesSuelo: $h2")
            }



        } else {
            println("Algunos arreglos están vacíos.")
        }
        println("-------------------------------------------------------------------------------------------------------------------------------------.")

    }
}
