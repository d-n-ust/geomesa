package org.locationtech.geomesa.jupyter

object L {
  import com.vividsolutions.jts.geom._
  import org.opengis.feature.`type`.AttributeDescriptor
  import org.opengis.feature.simple.SimpleFeature
  import org.apache.commons.lang3.StringEscapeUtils

  trait GeoRenderable {
    def render: String
  }

  trait Shape extends GeoRenderable

  case class StyleOptions(color: String = "#000000", fillColor: String = "#327A66", fillOpacity: Double = 0.75) {
    def render: String =
      s"""
         |{
         |  color: '$color',
         |  fillColor: '$fillColor',
         |  fillOpacity: '$fillOpacity'
         |}
       """.stripMargin
  }

  case class WMSLayer(layerName: String,
                      attributeName: String,
                      style: String = "",
                      filter: String = "INCLUDE",
                      color: String = "#FF0000",
                      geoserverURL: String = "http://stealthgs.ccri.com:8090/geoserver/wms",
                      env: Map[String,String] = Map.empty[String,String],
                      opacity: Double = 0.6,
                      transparent: Boolean = true) extends GeoRenderable {
    override def render: String =
      s"""
         | L.WMS.source('$geoserverURL?',
         |   {
         |      layers: '$layerName',
         |      cql_filter: "$filter",
         |      styles: '$style',
         |      env: '${env.map { case (k,v) => Array(k,v).mkString(sep = ":")}.mkString(sep = ";")}',
         |      transparent: '$transparent',
         |      opacity: $opacity,
         |      format: 'image/png',
         |      version: '1.1.1'
         |   }).getLayer('$layerName').addTo(map);
         |
       """.stripMargin
  }
  case class SimpleFeatureLayer(features: Seq[SimpleFeature], style: StyleOptions) extends GeoRenderable {
    override def render: String =
      s"""
         |L.geoJson(${features.map(simpleFeatureToGeoJSON).mkString("[",",","]")}).addTo(map);
       """.stripMargin

    private def simpleFeatureToGeoJSON(sf: SimpleFeature) = {
      import scala.collection.JavaConversions._
      s"""
         |{
         |    "type": "Feature",
         |    "properties": {
         |        ${sf.getType.getAttributeDescriptors.zip(sf.getAttributes).filter{case(a,b) => b != null}.map { case (d, a) => propToJson(d, a) }.mkString(sep =",\n")}
         |    },
         |    "geometry": {
         |        "type": "${sf.getDefaultGeometry.asInstanceOf[Geometry].getGeometryType}",
         |        "coordinates": ${processGeometry(sf.getDefaultGeometry.asInstanceOf[Geometry])}
         |    }
         |}
       """.stripMargin
    }

    private def propToJson(ad: AttributeDescriptor, a: Object) =
      if(a!= null) s""""${ad.getLocalName}": '${StringEscapeUtils.escapeJson(a.toString)}'"""
      else s"${ad.getLocalName}: ''"

    private def processGeometry(geom: Geometry) = geom.getGeometryType match {
      case "Point"      => processPoint(geom.asInstanceOf[Point])
      case "LineString" => processLinestring(geom.asInstanceOf[LineString])
      case "Polygon"    => processPoly(geom.asInstanceOf[Polygon])
    }

    def processCoord(c: Coordinate) = s"[${c.x}, ${c.y}]"
    def processPoint(p: Point) = s"${processCoord(p.getCoordinate)}"
    def processLinestring(l: LineString) = s"[${l.getCoordinates.map { c =>processCoord(c)}.mkString(",")}]"
    def processPoly(p: Polygon) = s"[${p.getCoordinates.map { c =>processCoord(c)}.mkString(",")}]"
  }

  case class Circle(cx: Double, cy: Double, radiusMeters: Double, style: StyleOptions) extends GeoRenderable {
    override def render: String =
      s"""
         |L.circle([$cy, $cx], $radiusMeters, ${style.render}).addTo(map);
       """.stripMargin
  }

  implicit class JTSPolyLayer(val poly: Polygon) extends GeoRenderable {
    override def render: String = ??? //TODO
  }

  implicit class JTSPointLayer(val point: Point) extends GeoRenderable {
    override def render: String = ??? //TODO
  }

  implicit class JTSLineStringLayer(val ls: LineString) extends GeoRenderable {
    override def render: String = ??? //TODO
  }

  def buildMap(layers: Seq[GeoRenderable], center: (Double, Double) = (0,0), zoom: Int = 8) =
    s"""
       |<html>
       |  <head>
       |    <link rel="stylesheet" href="https://unpkg.com/leaflet.wms@0.1.0/lib/leaflet.css" />
       |    <script src="https://unpkg.com/leaflet.wms@0.1.0/lib/leaflet.js"></script>
       |    <script src="https://unpkg.com/leaflet.wms@0.1.0/lib/leaflet.wms.js"></script>
       |    <script src="countries.geo.json" type="text/javascript"></script>
       |  </head>
       |  <body>
       |    <div id='map' style="width:1200px;height:800px"/>
       |    <script>
       |      // Initialize the Base Layer... Lodaded from GeoJson
       |      var basestyle = {"color": "#717171", "weight": 2, "opacity": 1.0};
       |      var base = L.geoJson(worldMap, basestyle);
       |
       |      //'map' is the id of the map
       |      var map = L.map('map', {
       |        crs: L.CRS.EPSG4326,
       |        center: [${center._1}, ${center._2}],
       |        zoom: ${zoom}
       |      });
       |
       |      map.addLayer(base);
       |      ${layers.map(_.render).mkString(sep = "\n")}
       |
       |    </script>
       |  </body>
       |</html>
     """.stripMargin

  def render(layers: Seq[GeoRenderable], center: (Double, Double) = (0,0), zoom: Int = 8) = {
    val id = s"map${org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric(5)}"
    s"""
       |  <iframe id="${id}" sandbox="allow-scripts allow-same-origin" style="border: none; width: 100%; height: 600px" srcdoc="${xml.Utility.escape(buildMap(layers, center, zoom))}"></iframe>
       |  <script>
       |    if (typeof resizeIFrame != 'function') {
       |      function resizeIFrame(el, k) {
       |        $$(el.contentWindow.document).ready(function() {
       |          el.style.height = el.contentWindow.document.body.scrollHeight + 'px';
       |        });
       |        if (k <= 10) { setTimeout(function() { resizeIFrame(el, k+1) }, 1000 + (k * 250)) };
       |      }
       |    }
       |    $$().ready( function() { resizeIFrame($$('#${id}').get(0), 1); });
       |  </script>
      """.stripMargin
  }

  def show(layers: Seq[GeoRenderable], center: (Double, Double) = (0,0), zoom: Int = 8)(implicit disp: String => Unit) = disp(render(layers,center,zoom))

  def print(layers: Seq[GeoRenderable], center: (Double, Double) = (0,0), zoom: Int = 8) = println(buildMap(layers,center,zoom))
}