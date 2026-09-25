package com.yulcaribe.app

import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.geometry.LatLngQuad
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.PropertyFactory.rasterOpacity
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textHaloColor
import org.maplibre.android.style.layers.PropertyFactory.textHaloWidth
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.ImageSource

private const val SOURCE_CHARTS = "yc-charts"
private const val SOURCE_NOTAMS = "yc-notams"
private const val SOURCE_FLIGHTS = "yc-flights"
private const val SOURCE_ROUTE = "yc-route"
private const val SOURCE_WAFS = "yc-wafs"

private const val LAYER_WAFS = "yc-wafs-raster"

private const val BASE_STYLE = """
{
  "version": 8,
  "glyphs": "https://demotiles.maplibre.org/font/{fontstack}/{range}.pbf",
  "sources": {
    "osm": {
      "type": "raster",
      "tiles": ["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],
      "tileSize": 256,
      "attribution": "© OpenStreetMap contributors"
    }
  },
  "layers": [
    {
      "id": "osm-base",
      "type": "raster",
      "source": "osm",
      "paint": {
        "raster-saturation": -0.82,
        "raster-brightness-min": 0.05,
        "raster-brightness-max": 0.42,
        "raster-contrast": 0.22
      }
    }
  ]
}
"""

@Composable
fun NativeAviationMap(
    modifier: Modifier = Modifier,
    center: Airport?,
    zoom: Double,
    interactive: Boolean,
    chartsGeoJson: String,
    notamGeoJson: String,
    flightsGeoJson: String,
    routeGeoJson: String = """{"type":"FeatureCollection","features":[]}""",
    routePoints: List<RoutePoint> = emptyList(),
    wafsFrame: WafsFrame?,
    onViewportChanged: (Viewport) -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val latestViewportCallback by rememberUpdatedState(onViewportChanged)
    var mapRef by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleReady by remember { mutableStateOf(false) }

    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
        }
    }

    DisposableEffect(mapView) {
        mapView.onStart()
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    AndroidView(
        factory = {
            mapView.apply {
                getMapAsync { map ->
                    mapRef = map
                    map.uiSettings.setAllGesturesEnabled(interactive)
                    map.uiSettings.isCompassEnabled = interactive
                    map.uiSettings.isAttributionEnabled = interactive
                    map.uiSettings.isLogoEnabled = false

                    map.setStyle(Style.Builder().fromJson(BASE_STYLE)) { style ->
                        setupSourcesAndLayers(style)
                        styleReady = true

                        center?.let { airport ->
                            map.cameraPosition = CameraPosition.Builder()
                                .target(LatLng(airport.lat, airport.lon))
                                .zoom(zoom)
                                .build()
                        }

                        map.addOnCameraIdleListener {
                            if (!interactive) return@addOnCameraIdleListener
                            val bounds = map.projection.visibleRegion.latLngBounds
                            latestViewportCallback(
                                Viewport(
                                    west = bounds.longitudeWest,
                                    south = bounds.latitudeSouth,
                                    east = bounds.longitudeEast,
                                    north = bounds.latitudeNorth,
                                    zoom = map.cameraPosition.zoom.toInt()
                                )
                            )
                        }
                    }
                }
            }
        },
        modifier = modifier.fillMaxSize()
    )

    LaunchedEffect(center?.icao, center?.lat, center?.lon, zoom, mapRef) {
        val map = mapRef ?: return@LaunchedEffect
        val airport = center ?: return@LaunchedEffect
        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(airport.lat, airport.lon),
                zoom
            ),
            650
        )
    }

    LaunchedEffect(chartsGeoJson, styleReady) {
        if (!styleReady) return@LaunchedEffect
        mapRef?.getStyle { style ->
            (style.getSource(SOURCE_CHARTS) as? GeoJsonSource)?.setGeoJson(chartsGeoJson)
        }
    }

    LaunchedEffect(notamGeoJson, styleReady) {
        if (!styleReady) return@LaunchedEffect
        mapRef?.getStyle { style ->
            (style.getSource(SOURCE_NOTAMS) as? GeoJsonSource)?.setGeoJson(notamGeoJson)
        }
    }

    LaunchedEffect(flightsGeoJson, styleReady) {
        if (!styleReady) return@LaunchedEffect
        mapRef?.getStyle { style ->
            (style.getSource(SOURCE_FLIGHTS) as? GeoJsonSource)?.setGeoJson(flightsGeoJson)
        }
    }

    LaunchedEffect(routeGeoJson, styleReady) {
        if (!styleReady) return@LaunchedEffect
        mapRef?.getStyle { style ->
            (style.getSource(SOURCE_ROUTE) as? GeoJsonSource)?.setGeoJson(routeGeoJson)
        }
    }

    LaunchedEffect(routePoints, styleReady, mapRef) {
        if (!styleReady || routePoints.size < 2) return@LaunchedEffect
        val map = mapRef ?: return@LaunchedEffect
        val builder = LatLngBounds.Builder()
        routePoints.forEach { builder.include(LatLng(it.lat, it.lon)) }
        runCatching {
            map.animateCamera(
                CameraUpdateFactory.newLatLngBounds(builder.build(), 72),
                700
            )
        }
    }

    LaunchedEffect(wafsFrame, styleReady) {
        if (!styleReady) return@LaunchedEffect
        mapRef?.getStyle { style ->
            if (wafsFrame == null) {
                if (style.getLayer(LAYER_WAFS) != null) style.removeLayer(LAYER_WAFS)
                if (style.getSource(SOURCE_WAFS) != null) style.removeSource(SOURCE_WAFS)
                return@getStyle
            }

            val bitmap = BitmapFactory.decodeByteArray(
                wafsFrame.png,
                0,
                wafsFrame.png.size
            ) ?: return@getStyle

            val maxLat = wafsFrame.maxLatitude.coerceIn(0.0, 85.0511)
            val quad = LatLngQuad(
                LatLng(maxLat, -180.0),
                LatLng(maxLat, 180.0),
                LatLng(-maxLat, 180.0),
                LatLng(-maxLat, -180.0)
            )

            val existing = style.getSource(SOURCE_WAFS) as? ImageSource
            if (existing != null) {
                existing.setCoordinates(quad)
                existing.setImage(bitmap)
            } else {
                style.addSource(ImageSource(SOURCE_WAFS, quad, bitmap))
                style.addLayerAbove(
                    RasterLayer(LAYER_WAFS, SOURCE_WAFS).withProperties(
                        rasterOpacity(0.46f)
                    ),
                    "osm-base"
                )
            }
        }
    }
}

private fun setupSourcesAndLayers(style: Style) {
    style.addSource(GeoJsonSource(SOURCE_CHARTS, emptyGeoJson()))
    style.addSource(GeoJsonSource(SOURCE_NOTAMS, emptyGeoJson()))
    style.addSource(GeoJsonSource(SOURCE_FLIGHTS, emptyGeoJson()))
    style.addSource(GeoJsonSource(SOURCE_ROUTE, emptyGeoJson()))

    style.addLayer(
        FillLayer("yc-airspace-fill", SOURCE_CHARTS)
            .withFilter(layerIs("airspace"))
            .withProperties(
                fillColor("#8d7cff"),
                fillOpacity(0.07f)
            )
    )
    style.addLayer(
        LineLayer("yc-airspace-line", SOURCE_CHARTS)
            .withFilter(layerIs("airspace"))
            .withProperties(
                lineColor("#9c8cff"),
                lineWidth(1.1f),
                lineOpacity(0.68f)
            )
    )

    addRouteLayer(style, "airway", "#00d9ff", 1.4f)
    addRouteLayer(style, "sid", "#67e9a1", 2.1f)
    addRouteLayer(style, "star", "#c4a1ff", 2.1f)

    addPointLayer(style, "airport", "#7aeaff", 6.0f)
    addPointLayer(style, "navaid", "#ffd35f", 4.4f)
    addPointLayer(style, "waypoint", "#d9e1e8", 3.2f)

    style.addLayer(
        FillLayer("yc-notam-fill", SOURCE_NOTAMS)
            .withFilter(Expression.eq(Expression.geometryType(), Expression.literal("Polygon")))
            .withProperties(
                fillColor("#ff6f91"),
                fillOpacity(0.11f)
            )
    )
    style.addLayer(
        LineLayer("yc-notam-line", SOURCE_NOTAMS)
            .withProperties(
                lineColor("#ff7f94"),
                lineWidth(1.7f),
                lineOpacity(0.78f)
            )
    )
    style.addLayer(
        CircleLayer("yc-notam-point", SOURCE_NOTAMS)
            .withFilter(Expression.eq(Expression.geometryType(), Expression.literal("Point")))
            .withProperties(
                circleColor("#ff7f94"),
                circleRadius(5.0f),
                circleStrokeColor("#081116"),
                circleStrokeWidth(1.2f)
            )
    )

    style.addLayer(
        LineLayer("yc-route-line", SOURCE_ROUTE)
            .withProperties(
                lineColor("#00d9ff"),
                lineWidth(3.2f),
                lineOpacity(0.95f)
            )
    )

    style.addLayer(
        CircleLayer("yc-flight-dot", SOURCE_FLIGHTS)
            .withProperties(
                circleColor("#f5f7fa"),
                circleRadius(4.4f),
                circleStrokeColor("#00d9ff"),
                circleStrokeWidth(1.1f)
            )
    )
    style.addLayer(
        SymbolLayer("yc-flight-label", SOURCE_FLIGHTS)
            .withProperties(
                textField(Expression.get("flight")),
                textSize(10.0f),
                textColor("#f5f7fa"),
                textHaloColor("#06111a"),
                textHaloWidth(1.2f)
            )
    )
}

private fun addRouteLayer(
    style: Style,
    layer: String,
    color: String,
    width: Float
) {
    style.addLayer(
        LineLayer("yc-" + layer + "-line", SOURCE_CHARTS)
            .withFilter(layerIs(layer))
            .withProperties(
                lineColor(color),
                lineWidth(width),
                lineOpacity(if (layer == "airway") 0.76f else 0.90f)
            )
    )
}

private fun addPointLayer(
    style: Style,
    layer: String,
    color: String,
    radius: Float
) {
    style.addLayer(
        CircleLayer("yc-" + layer + "-point", SOURCE_CHARTS)
            .withFilter(layerIs(layer))
            .withProperties(
                circleColor(color),
                circleRadius(radius),
                circleStrokeColor("#06111a"),
                circleStrokeWidth(1.1f)
            )
    )
    style.addLayer(
        SymbolLayer("yc-" + layer + "-label", SOURCE_CHARTS)
            .withFilter(layerIs(layer))
            .withProperties(
                textField(Expression.get("ident")),
                textSize(if (layer == "airport") 12.0f else 10.0f),
                textColor(color),
                textHaloColor("#06111a"),
                textHaloWidth(1.2f)
            )
    )
}

private fun layerIs(name: String): Expression =
    Expression.eq(Expression.get("layer"), Expression.literal(name))

private fun emptyGeoJson(): String =
    """{"type":"FeatureCollection","features":[]}"""
