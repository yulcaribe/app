package com.yulcaribe.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
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
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconRotate
import org.maplibre.android.style.layers.PropertyFactory.iconSize
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textHaloColor
import org.maplibre.android.style.layers.PropertyFactory.textHaloWidth
import org.maplibre.android.style.layers.PropertyFactory.textRotate
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.ImageSource

private const val V2_CHARTS = "v2-charts"
private const val V2_NOTAMS = "v2-notams"
private const val V2_AIRCRAFT = "v2-aircraft"
private const val V2_ROUTE = "v2-route"
private const val V2_BRIEF = "v2-brief"
private const val V2_WAFS = "v2-wafs"
private const val V2_WAFS_LAYER = "v2-wafs-layer"

private const val V2_BASE_STYLE = """
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
fun NativeAviationMapV2(
    modifier: Modifier = Modifier,
    center: Airport? = null,
    zoom: Double = 7.0,
    interactive: Boolean,
    chartsGeoJson: String = emptyGeoJsonV2(),
    notamGeoJson: String = emptyGeoJsonV2(),
    aircraftGeoJson: String = emptyGeoJsonV2(),
    routeGeoJson: String = emptyGeoJsonV2(),
    briefingGeoJson: String = emptyGeoJsonV2(),
    routePoints: List<RoutePoint> = emptyList(),
    fitRoute: Boolean = false,
    wafsFrame: WafsFrame? = null,
    wafsOpacity: Float = 0.48f,
    onViewportChanged: (Viewport) -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val latestViewportCallback by rememberUpdatedState(onViewportChanged)
    var mapRef by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleReady by remember { mutableStateOf(false) }

    val mapView = remember {
        MapView(context).apply { onCreate(null) }
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

                    map.setStyle(Style.Builder().fromJson(V2_BASE_STYLE)) { style ->
                        setupV2(style)
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

    LaunchedEffect(interactive, mapRef) {
        mapRef?.uiSettings?.setAllGesturesEnabled(interactive)
        mapRef?.uiSettings?.isCompassEnabled = interactive
        mapRef?.uiSettings?.isAttributionEnabled = interactive
    }

    LaunchedEffect(center?.icao, center?.lat, center?.lon, zoom, mapRef, styleReady) {
        if (!styleReady) return@LaunchedEffect
        val map = mapRef ?: return@LaunchedEffect
        val airport = center ?: return@LaunchedEffect
        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(airport.lat, airport.lon), zoom),
            420
        )
    }

    LaunchedEffect(chartsGeoJson, styleReady) {
        updateGeo(mapRef, styleReady, V2_CHARTS, chartsGeoJson)
    }
    LaunchedEffect(notamGeoJson, styleReady) {
        updateGeo(mapRef, styleReady, V2_NOTAMS, notamGeoJson)
    }
    LaunchedEffect(aircraftGeoJson, styleReady) {
        updateGeo(mapRef, styleReady, V2_AIRCRAFT, aircraftGeoJson)
    }
    LaunchedEffect(routeGeoJson, styleReady) {
        updateGeo(mapRef, styleReady, V2_ROUTE, routeGeoJson)
    }
    LaunchedEffect(briefingGeoJson, styleReady) {
        updateGeo(mapRef, styleReady, V2_BRIEF, briefingGeoJson)
    }

    LaunchedEffect(routePoints, fitRoute, styleReady, mapRef) {
        if (!fitRoute || !styleReady || routePoints.size < 2) return@LaunchedEffect
        val map = mapRef ?: return@LaunchedEffect
        val builder = LatLngBounds.Builder()
        routePoints.forEach { builder.include(LatLng(it.lat, it.lon)) }
        runCatching {
            map.animateCamera(
                CameraUpdateFactory.newLatLngBounds(builder.build(), 64),
                520
            )
        }
    }

    LaunchedEffect(wafsFrame, wafsOpacity, styleReady) {
        if (!styleReady) return@LaunchedEffect
        mapRef?.getStyle { style ->
            if (wafsFrame == null) {
                if (style.getLayer(V2_WAFS_LAYER) != null) style.removeLayer(V2_WAFS_LAYER)
                if (style.getSource(V2_WAFS) != null) style.removeSource(V2_WAFS)
                return@getStyle
            }

            val bitmap = BitmapFactory.decodeByteArray(wafsFrame.png, 0, wafsFrame.png.size)
                ?: return@getStyle
            val maxLat = wafsFrame.maxLatitude.coerceIn(0.0, 85.0511)
            val quad = LatLngQuad(
                LatLng(maxLat, -180.0),
                LatLng(maxLat, 180.0),
                LatLng(-maxLat, 180.0),
                LatLng(-maxLat, -180.0)
            )

            val existing = style.getSource(V2_WAFS) as? ImageSource
            if (existing != null) {
                existing.setCoordinates(quad)
                existing.setImage(bitmap)
                style.getLayer(V2_WAFS_LAYER)?.setProperties(
                    rasterOpacity(wafsOpacity.coerceIn(0.08f, 0.95f))
                )
            } else {
                style.addSource(ImageSource(V2_WAFS, quad, bitmap))
                style.addLayerAbove(
                    RasterLayer(V2_WAFS_LAYER, V2_WAFS).withProperties(
                        rasterOpacity(wafsOpacity.coerceIn(0.08f, 0.95f))
                    ),
                    "osm-base"
                )
            }
        }
    }
}

private fun updateGeo(
    map: MapLibreMap?,
    ready: Boolean,
    sourceId: String,
    geoJson: String
) {
    if (!ready) return
    map?.getStyle { style ->
        (style.getSource(sourceId) as? GeoJsonSource)?.setGeoJson(geoJson)
    }
}

private fun setupV2(style: Style) {
    style.addImage("v2-aircraft-icon", aircraftBitmapV2())
    style.addSource(GeoJsonSource(V2_CHARTS, emptyGeoJsonV2()))
    style.addSource(GeoJsonSource(V2_NOTAMS, emptyGeoJsonV2()))
    style.addSource(GeoJsonSource(V2_AIRCRAFT, emptyGeoJsonV2()))
    style.addSource(GeoJsonSource(V2_ROUTE, emptyGeoJsonV2()))
    style.addSource(GeoJsonSource(V2_BRIEF, emptyGeoJsonV2()))

    style.addLayer(
        FillLayer("v2-airspace-fill", V2_CHARTS)
            .withFilter(layerEqV2("airspace"))
            .withProperties(fillColor("#ff7f94"), fillOpacity(0.055f))
    )
    style.addLayer(
        LineLayer("v2-airspace-line", V2_CHARTS)
            .withFilter(layerEqV2("airspace"))
            .withProperties(lineColor("#ff7f94"), lineWidth(1.2f), lineOpacity(0.66f))
    )

    addLineV2(style, "airway", "#5fdbe8", 1.25f)
    addLineV2(style, "sid", "#70e8a7", 2.1f)
    addLineV2(style, "star", "#bc9cff", 2.1f)

    addPointV2(style, "airport", "#7ee7ff", 5.6f)
    addPointV2(style, "navaid", "#ffc76b", 4.4f)
    addPointV2(style, "waypoint", "#d6e1e7", 3.4f)

    style.addLayer(
        FillLayer("v2-notam-fill", V2_NOTAMS)
            .withProperties(fillColor("#ffd35f"), fillOpacity(0.10f))
    )
    style.addLayer(
        LineLayer("v2-notam-line", V2_NOTAMS)
            .withProperties(lineColor("#ffd35f"), lineWidth(1.7f), lineOpacity(0.82f))
    )
    style.addLayer(
        CircleLayer("v2-notam-point", V2_NOTAMS)
            .withProperties(
                circleColor("#ffd35f"),
                circleRadius(5.0f),
                circleStrokeColor("#071019"),
                circleStrokeWidth(1.2f)
            )
    )

    style.addLayer(
        LineLayer("v2-route", V2_ROUTE)
            .withProperties(lineColor("#31e4ff"), lineWidth(3.4f), lineOpacity(0.96f))
    )

    style.addLayer(
        FillLayer("v2-hazard-fill", V2_BRIEF)
            .withFilter(Expression.eq(Expression.get("kind"), Expression.literal("hazard")))
            .withProperties(fillColor("#ff6472"), fillOpacity(0.11f))
    )
    style.addLayer(
        LineLayer("v2-hazard-line", V2_BRIEF)
            .withFilter(Expression.eq(Expression.get("kind"), Expression.literal("hazard")))
            .withProperties(lineColor("#ff6472"), lineWidth(1.7f), lineOpacity(0.86f))
    )

    style.addLayer(
        CircleLayer("v2-station-dot", V2_BRIEF)
            .withFilter(Expression.eq(Expression.get("kind"), Expression.literal("station")))
            .withProperties(
                circleColor("#7aeaff"),
                circleRadius(5.8f),
                circleStrokeColor("#071019"),
                circleStrokeWidth(1.3f)
            )
    )
    style.addLayer(
        SymbolLayer("v2-station-label", V2_BRIEF)
            .withFilter(Expression.eq(Expression.get("kind"), Expression.literal("station")))
            .withProperties(
                textField(Expression.get("label")),
                textSize(10.0f),
                textColor("#7aeaff"),
                textHaloColor("#071019"),
                textHaloWidth(1.3f)
            )
    )

    style.addLayer(
        CircleLayer("v2-wind-dot", V2_BRIEF)
            .withFilter(Expression.eq(Expression.get("kind"), Expression.literal("wind")))
            .withProperties(
                circleColor("#071019"),
                circleRadius(3.8f),
                circleStrokeColor("#31e4ff"),
                circleStrokeWidth(1.2f)
            )
    )
    style.addLayer(
        SymbolLayer("v2-wind-arrow", V2_BRIEF)
            .withFilter(Expression.eq(Expression.get("kind"), Expression.literal("wind")))
            .withProperties(
                textField("↑"),
                textSize(18.0f),
                textColor("#31e4ff"),
                textHaloColor("#071019"),
                textHaloWidth(1.7f),
                textRotate(Expression.get("rotation"))
            )
    )
    style.addLayer(
        SymbolLayer("v2-wind-speed", V2_BRIEF)
            .withFilter(Expression.eq(Expression.get("kind"), Expression.literal("wind")))
            .withProperties(
                textField(Expression.get("speedLabel")),
                textSize(9.0f),
                textColor("#f4f8fb"),
                textHaloColor("#071019"),
                textHaloWidth(1.4f)
            )
    )

    style.addLayer(
        SymbolLayer("v2-aircraft-icon-layer", V2_AIRCRAFT)
            .withProperties(
                iconImage("v2-aircraft-icon"),
                iconSize(0.72f),
                iconRotate(Expression.get("track")),
                iconAllowOverlap(true)
            )
    )
    style.addLayer(
        SymbolLayer("v2-aircraft-label", V2_AIRCRAFT)
            .withProperties(
                textField(Expression.get("flight")),
                textSize(10.0f),
                textColor("#f4f8fb"),
                textHaloColor("#071019"),
                textHaloWidth(1.5f)
            )
    )
}

private fun addLineV2(style: Style, layer: String, color: String, width: Float) {
    style.addLayer(
        LineLayer("v2-$layer-line", V2_CHARTS)
            .withFilter(layerEqV2(layer))
            .withProperties(lineColor(color), lineWidth(width), lineOpacity(0.82f))
    )
}

private fun addPointV2(style: Style, layer: String, color: String, radius: Float) {
    style.addLayer(
        CircleLayer("v2-$layer-point", V2_CHARTS)
            .withFilter(layerEqV2(layer))
            .withProperties(
                circleColor(color),
                circleRadius(radius),
                circleStrokeColor("#071019"),
                circleStrokeWidth(1.1f)
            )
    )
    style.addLayer(
        SymbolLayer("v2-$layer-label", V2_CHARTS)
            .withFilter(layerEqV2(layer))
            .withProperties(
                textField(Expression.get("ident")),
                textSize(if (layer == "airport") 11.0f else 9.5f),
                textColor(color),
                textHaloColor("#071019"),
                textHaloWidth(1.3f)
            )
    )
}

private fun layerEqV2(name: String): Expression =
    Expression.eq(Expression.get("layer"), Expression.literal(name))

private fun emptyGeoJsonV2(): String =
    """{"type":"FeatureCollection","features":[]}"""


private fun aircraftBitmapV2(): Bitmap {
    val size = 64
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(244, 248, 251)
        style = Paint.Style.FILL
    }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(7, 16, 25)
        style = Paint.Style.STROKE
        strokeWidth = 2.4f
        strokeJoin = Paint.Join.ROUND
    }
    val path = Path().apply {
        moveTo(32f, 3f)
        cubicTo(29.8f, 3f, 28.7f, 5.4f, 28.4f, 8.4f)
        lineTo(26.8f, 25.2f)
        lineTo(7f, 34.4f)
        lineTo(7f, 39f)
        lineTo(27.8f, 34.4f)
        lineTo(28.2f, 49.5f)
        lineTo(20.2f, 55.5f)
        lineTo(20.2f, 59f)
        lineTo(32f, 56f)
        lineTo(43.8f, 59f)
        lineTo(43.8f, 55.5f)
        lineTo(35.8f, 49.5f)
        lineTo(36.2f, 34.4f)
        lineTo(57f, 39f)
        lineTo(57f, 34.4f)
        lineTo(37.2f, 25.2f)
        lineTo(35.6f, 8.4f)
        cubicTo(35.3f, 5.4f, 34.2f, 3f, 32f, 3f)
        close()
    }
    canvas.drawPath(path, fill)
    canvas.drawPath(path, stroke)
    return bitmap
}
