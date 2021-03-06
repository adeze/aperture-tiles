---
section: Docs
subsection: Development
chapter: How-To
topic: App Configuration
permalink: docs/development/how-to/configuration/
layout: submenu
---

# App Configuration #

Once you have generated a tile set from your source data, you should configure and deploy a Tile Server and a Tile Client to make your visual analytic viewable in a web browser. The Tile Server serves tiles from your pyramid, while the Tile Client is a simple web client to view those tiles on a map or plot. 

Aperture Tiles supports two types of tile rendering:

- **Server-side rendering**, in which the Server renders the tiles as image files and passes them on to the Client.
- **Client-side rendering**, in which the Server passes the tiles as JSON data to the Client, which then renders them directly.

The fastest way to create a Tile Server and Tile Client is to modify an existing example application, which are available in the source code (*tile-examples/*). These examples will help you understand the structure and configuration of the Tile Server and Tile Client.

## <a name="template-setup"></a> Template Setup ##

To begin configuring your Tile Server and Tile Client:

1. Choose the Julia demo (*tile-examples/julia-demo/*) or Twitter Topics demo (*tile-examples/twitter-topics/*) to use as a template. The Julia demo displays a server-rendered heatmap on a blank crossplot baselayer, while the Twitter Topics demo displays a server-rendered heatmap and client-rendered word clouds on a geographic Google Maps baselayer.
2. Copy the appropriate demo and give it a unique name (e.g., *new-project*).
3. Update the Gradle build file (*new-project/build.gradle*) to add or change the following fields:
	- `description`: Enter an appropriate project description. 
	- `group`: Enter an appropriate group ID.
	- `version`: Enter an appropriate project version number.

## <a name="server-config"></a> Tile Server Configuration ##

The Tiler Server in your new template relies on the following configuration files:

- [Web XML](#webxml), which defines which modules Guice will use.
- [Tile Properties](#tileproperties), which specifies constants used by Guice during initialization, including the location of your Spark installation, and the location of some server configuration files.
- [Layers](#layers), which defines the individual layers of data that can be overlaid on your base map. The layers file also indicates whether rendering should be performed by the server or the client.

The remainder of the tile server configuration, which includes instantiation of the map, its base features and axis configuration, should be handled in the [Application JavaScript](#app-js) (*/src/main/webapp/js/***app.js**).

### <a name="webxml"></a> Web XML ###

Edit the client **web.xml** file in *new-project/src/main/webapp/WEB-INF/*:

1. If you performed a custom tile generation, edit the guice-modules parameter to pass in any custom modules you created (e.g., your custom Tile Serialization Factory).
2. If required, uncomment the relevant Spark lines in the guice-modules to enable live tiling or drill-through to raw data.
		
### <a name="tileproperties"></a> Tile Properties ###

Edit the client **tile.properties** file in *new-project/src/main/resources/*. This file specifies parameters for use by Guice, such as the location of your layer and annotation directories. Additional Spark parameters are available for on-demand tile generation and data drill down:

- The location of your Spark master
- The name under which the Aperture Tiles web service should appear in the Spark web interface.
- The home directory of Spark
- Any JARs you want to add to the Spark context

### <a name="layers"></a> Layers ##

The layers file describes the tile layers to be made available to the server and client application. Parameters in the layers file are split into two sections: those in the public node are accessible from the client, while those under the private note are not.

Layer file examples can be found in in the Julia example at *tile-examples\julia-demo\src\main\resources\layers* and the Twitter Topics example at *tile-examples\twitter-topics\twitter-topics-client\src\main\resources\layers*.

#### <a name="layer-id"></a> ID ####

The ID parameter uniquely identifies the layer.

<div class="details props">
	<div class="innerProps">
		<ul class="methodDetail" id="MethodDetail">
			<dl class="detailList params">
				<dt>id</dt>
				<dd>The identification string for the layer. This is used in all layer-related REST calls. Must conform to JSON property name format.</dd>
			</dl>
		</ul>
	</div>
</div>

#### Public Parameters ####

Parameters in the **public** node section of the **layers.json** file are accessible from the client.

##### <a name="layer-pyramid"></a> Pyramid #####

The pyramid parameters describe the extent of the data in the layer. The values that you provide in this section must match the values in your data source and in your map configuration.

For cross plot maps, the `type` should always be set to *AreaOfInterest*. Also include the minimum and maximum values on the X and Y axes in your cross plot. 

```json
pyramid: {
	type: "AreaOfInterest",
	minX : 1,
	maxX : 6336769,
	minY : 0,
	maxY : 500000
},
```

For geographic maps, the `type` should always be set to *WebMercator*. No minimum and maximum X and Y values are required for this layer type.

```json
pyramid: {
	type: "WebMercator"
},
```

**NOTE**: Your layer and map pyramid configurations much match each other.

##### <a name="layer-renderers"></a> Renderers #####

This option defines which renderer the server should use to render tiles. The renderer will be dependent on the type of tile data. The current renderer options are:

<div class="details props">
	<div class="innerProps">
		<ul class="methodDetail" id="MethodDetail">
			<dl class="detailList params">				
				<dt>renderer</dt>
				<dd>Used for server-side rendering.
					
					<dl>
						<dt>type</dt>
						<dd>
							<dl>
								<dt>'heatmap'</dt>
								<dd>Renders to a heat-map, based on a standard Avro double-valued tile</dd>
								
								<dt>'doubleseries'</dt>
								<dd>Renders to a series of heat-maps, based on a standard Avro double-series-valued tile</dd>
								
								<dt>'doublestatistics'</dt>
								<dd>Renders tile's total hit and % coverage as text to the tile</dd>
								
								<dt>'textscore'</dt>
								<dd>Renders to an image showing scored words, with bars based on their score</dd>
							</dl>
						</dd>
					
						<dt>ramp</dt>
						<dd>Determines the color scale applied to the data points based on their concentration. The default color scales are:
							<ul>
								<li>'hot': A warm orange ramp.
								<li>'neutral': A black-grey-white ramp.
								<li>'cool': A cool blue ramp.
								<li>'spectral': A red-green-yellow ramp.
								<li>'flat': A single color (white) ramp.
							</ul>
						</dd>
						
						<dt>rangeMin</dt>
						<dd>The minimum percentage to clamp the low end of the color ramp.</dd>

						<dt>rangeMax</dt>
						<dd>The maximum percentage to clamp the low end of the color ramp.</dd>

						<dt>opacity</dt>
						<dd>Opacity of the rendered tile layer expressed as a decimal ranging from 0 (completely transparent) to 1 (completely opaque).</dd>

						<dt>enabled</dt>
						<dd>Indicates whether the layer is enabled on load.</dd>
					</dl>
				</dd>
			</dl>
		</ul>
	</div>
</div>

##### Value Transformer #####

Type of transformations that can be applied to the data values when determining color:

<div class="details props">
	<div class="innerProps">
		<ul class="methodDetail" id="MethodDetail">
			<dl class="detailList params">
				<dt>valueTransform</dt>
				<dd>
					<dl>
						<dt>'type'</dt>
						<dd>Value transformer type:
							<dl>
								<dt>minmax</dt>
								<dd>Do not change the value, but allow capping within min and max values.</dd>
								
								<dt>sigmoid</dt>
								<dd>Apply a sigmoid function to each value to make them fall between fixed values. With data that can be infinitely large in either direction, this can be used to bring it into finite, displayable bounds. Uses the following properties to indicate the minimum and maximum expected values while allowing values infinitely above or below them.
								<ul>
									<li>layerMin
									<li>layerMax
								</ul>
								These two values should be symmetric around the desired central value.</dd>

								<dt>half-sigmoid</dt>
								<dd>Apply a sigmoid function to each value to make them fall between fixed values. With data that can be infinitely large in one direction only, this can be used to bring it into finite, displayable bounds. Uses the following properties:
								<ul>
									<li><em>layerMin</em> corresponds to the minimum allowed data value
									<li><em>layerMax</em> indicates the maximum expected data value while allowing values infinitely above it
								</ul></dd>
								
								<dt>log10</dt>
								<dd>Take the log (base 10) of each value. Useful when displaying data that can be large (but not infinite) in one direction only. Uses the <em>layerMax</em> property to indicate the maximum.</dd>
							</dl>
						</dd>
					</dl>
				</dd>
			</dl>
		</ul>
	</div>
</div>

#### Private Parameters ####

Parameters in the **private** node section of the **layers.json** file are not accessible from the client.

##### <a name="layer-data"></a> Data #####

The data parameters specify the location of the tiles that you created. If you are using HBase, separate parameters are required.

<div class="details props">
	<div class="innerProps">
		<ul class="methodDetail" id="MethodDetail">
			<dl class="detailList params">
				<dt>id</dt>
				<dd>Must match the name of the folder to which the tiles were saved during the generation process, which is composed of the following parameters from the Tiling Property File:
	
				<br><br>&lt;oculus.binning.name&gt;.&lt;oculus.binning.xField&gt;.&lt;oculus.binning.yField&gt;.
				&lt;oculus.binning.valueField&gt;</dd>

				<br><br>Where oculus.binning.yField is set to 0 if no yField name is specified.

				<dt>pyramidio</dt>
				<dd>
					<dl>
						<dt>type</dt>
						<dd>Indicates the file format of your tiles:
							<dl>
								<dt>hbase</dt>
								<dd>Tiles are stored in HBase</dd>
								
								<dt>file</dt>
								<dd>Tiles are stored in an uncompressed directory in a local filesystem</dd>

								<dt>zip</dt>
								<dd>Tiles are stored in compressed file in a local filesystem</dd>
							</dl>
						</dd>
						
						<dt>root.path</dt>
						<dd>Root path in which the tiles are stored. Not used for HBase.</dd>
						
						<dt>extension</dt>
						<dd>Name of the compressed file in which tiles are stored. Only used for ZIP
						files.</dd>
						
						<dt>hbase.zookeeper.quorum</dt>
						<dd>Zookeeper quorum location needed to connect to HBase.</dd>
						
						<dt>hbase.zookeeper.port</dt>
						<dd>Port through which to connect to zookeeper. Typically defaults to 2181.</dd>
						
						<dt>hbase.master</dt>
						<dd>Location of the HBase master on which the tiles are saved.</dd>
						
					</dl>
				</dd>
			</dl>
		</ul>
	</div>
</div>

## Application JavaScript ##

The application JavaScript file (*/src/main/webapp/js/***app.js**) should request layers from server and return an array of layer configuration objects. Among the objects it should instantiate are the map, its baselayer and axis configurations.

### <a name="maps"></a> Map ###

The map describes the base map upon which your source data is projected:

- Geographic maps: the pyramid `type` parameter is *WebMercator*. If no `type` is specified, the map defaults to *WebMercator* and no other configuration options are required.
- Non-geographic cross-plot maps: the `type` should always be set to *AreaOfInterest*. Additional pyramid parameters are required to describe the minimum and maximum values on the X and Y axes. The values that you provide in this section must match the values in your data source.

```json
pyramid: {
	type : "AreaOfInterest",
	minX : -2,
	maxX : 2,
	minY : -2,
	maxY : 2
	},
```

**NOTE**: Your layer and map pyramid configurations must match each other.

### <a name="geo-baselayer"></a> BaseLayer ###

The BaseLayer parameters use map provider APIs to determine what features to include on the base map. In the following example, the Google Maps API (OpenLayers.Layer.Google) is used to define the style of the base map.

```json
baseLayer = new tiles.BaseLayer({
    type: "Google",
    options : {
       styles : [
            { featureType: "all",
              stylers : [ { invert_lightness : true },
                            { saturation : -100 },
                            { visibility : "simplified" } ] },
            { featureType: "administrative",
              elementType: "geometry",
                stylers: [ { visibility: "off" } ] },
            { featureType : "landscape.natural.landcover",
              stylers : [ { visibility : "off" } ] },
            { featureType : "road",
              stylers : [ { visibility : "on" } ] },
            { featureType : "landscape.man_made",
              stylers : [ { visibility : "off" } ] },
            { featureType : "landscape",
              stylers : [ { lightness : "-100" } ] },
            { featureType : "poi",
              stylers : [ { visibility : "off" } ] },
            { featureType : "administrative.country",
              elementType : "geometry",
              stylers : [ { visibility : "on" },
                            { lightness : -56 } ] },
            { elementType : "labels",
              stylers : [ { lightness : -46 },
                            { visibility : "on" } ] }
        ]
    }
});
```

The next example shows a TMS layer configuration (standard OpenLayers.Layer.TMS) that uses the Uncharted World Graphite map set. You can use these maps in offline mode by first downloading the [map tiles WAR](http://aperturejs.com/downloads/) on [aperturejs.com](http://aperturejs.com/).

```javascript
{
	"type": "TMS",
	"url" : "http://aperture.oculusinfo.com/map-world-graphite/",
	"options" : {
	  "name" : "Open Graphite",
	  "layername": "world-graphite",
	  "osm": 0,
	  "type": "png",
	  "serverResolutions": [156543.0339,78271.51695,39135.758475,19567.8792375,9783.93961875,4891.96980938,2445.98490469,1222.99245234,611.496226172],
	  "resolutions": [156543.0339,78271.51695,39135.758475,19567.8792375,9783.93961875,4891.96980938,2445.98490469,1222.99245234,611.496226172]
	}
}
```

### <a name="cross-axisconfig"></a> Axes ###

The AxisConfig parameters determine how the X and Y axes are drawn in your cross plot map.

<div class="details props">
	<div class="innerProps">
		<ul class="methodDetail" id="MethodDetail">
			<dl class="detailList params">
				<dt>position</dt>
				<dd>Axis type, where <em>bottom</em> denotes the x-axis and <em>left</em> denotes the y-axis.</dd>
				
				<dt>title</dt>
				<dd>Axis name (e.g., <em>Longitude</em> or <em>Latitude</em>).</dd>
				
				<dt>enabled</dt>
				<dd>Indicates whether the axes are displayed (<em>true</em>) or hidden (<em>false</em>) when a new session begins.</dd>

				<dt>repeat</dt>
				<dd>Indicates whether the map will repeat when the user scrolls off one end. Most useful for geographic maps.</dd>
	
				<dt>intervals</dt>
				<dd>
				
					<dl>
						<dt>type</dt>
						<dd>How the following increment value is calculated based on the axis range. Accepted values include <em>percentage</em>, <em>%</em>, <em>value</em> or <em>#</em>.</dd>
		
						<dt>increment</dt>
						<dd>Value or percentage of units by which to increment the intervals. How this is applied is dependent on the specified type.</dd>
						
						<dt>pivot</dt>
						<dd>Value or percentage from which all other values are incremented. Typically <em>0</em>.</dd>
		
						<dt>scaleByZoom</dt>
						<dd>Indicates whether the axis should be scaled by the zoom factor (<em>true</em>/<em>false</em>).</dd>
					</dl>
				</dd>
				
				<dt>units</dt>
				<dd>
				
					<dl>
						<dt>type</dt>
						<dd>Determines the individual axis label strings formats. Options include:
							<ul>
								<li>"billions": 150.25B
								<li>"millions": 34.45M
								<li>"thousands": 323.26K
								<li>"decimal": 234243.32
								<li>"integer": 563554
								<li>"time": MM/DD/YYYY
								<li>"degrees": 34.56&#176;
							</ul>
						</dd>
						
						<dt>decimals</dt>
						<dd>Number of decimals to display for each unit. Applicable to <em>billions</em>, <em>millions</em>, <em>thousands</em> and <em>decimal</em> types.</dd>
		
						<dt>stepDown</dt>
						<dd>Indicates whether the units can step down if they are below range (<em>true</em>/<em>false</em>). Applicable to <em>billions</em>, <em>millions</em>, <em>thousands</em> types.</dd>
					</dl>
				</dd>
			</dl>
		</ul>
	</div>
</div>

## <a name="clientconfig"></a> Tile Client Configuration ##

### <a name="clientside"></a> Client-Side Rendering ###

The previous sections focus largely on the process of implementing an Aperture Tiles application using server-side tile rendering (where the Server renders the tiles as image files and passes them to the Client). The process of implementing an application using client-side tile rendering (where the Server passes the tiles as JSON data to the Client, which then renders them directly) requires custom code.

A sample application using this method is available in the Aperture Tiles source code at */tile-examples/twitter-topics/twitter-topics-client/*. The Twitter Topics application uses client-side rendering to draw the top words occurring in each tile. As multiple renderers are attached to this client-side layer. The custom renderers for this application are available in */tile-client/src/js/layer/renderer/*.

Line 33 requests the layer configuration objects from the server, passing them to the supplied callback function as arguments.

```javascript
tiles.LayerService.getLayers( function( layers ) { 
	... 
});
```

Line 39 organizes the layer configuration object array into a map keyed by layer ID. It also parses the metadata JSON strings into their respective runtime objects. This ensures support for legacy layer metadata.

```javascript
layers = tiles.LayerUtil.parse( layers.layers );
```

Lines 180-184 instantiate a render theme object that styles the rendered components.

```javascript
darkRenderTheme = new tiles.RenderTheme( "dark", {
    'color': "#FFFFFF",
    'color:hover': "#09CFFF",
    'text-shadow': "#000"
});
```

Lines 204-216 instantiate a word cloud renderer, attaching the theme to the 'text' render target and appending a 'hook' function to give access to the rendered DOM elements and respective data entry.

Note the 'textKey' and 'countKey' attributes under the 'text' render target. They specify that the renderer will find the 'text' and 'count' values required to build the word cloud under the attributes 'topic' and 'countMonthly' in the data entry.

```javascript
wordCloudRenderer = new tiles.WordCloudRenderer({
    text: {
        textKey: "topic",
        countKey: "countMonthly",
        themes: [ darkRenderTheme ]
    },
    hook: function( elem, entry, entries, data ) {
        elem.onclick = function() {
            console.log( elem );
            console.log( entry );
        };
    }
});
```

The client-rendered layer is instantiated on lines 234-237, passing the "top-tweets" layer as its source and the word cloud renderer as its renderer.

```javascript
clientLayer = new tiles.ClientLayer({
    source: layers["top-tweets"],
    renderer: wordCloudRenderer
});
```

Finally the map is instantiated, along with all its components, including the client layer.

```javascript
map = new tiles.Map( "map" );
map.add( clientLayer );
```

## <a name="deployment"></a> Deployment ##

Once you have finished configuring the map and layer properties, copy the `/new-project/` folder to your Apache Tomcat or Jetty server.

Access the `/new-project/` directory on the server from any web browser to to view your custom Aperture Tiles visual analytic.

## Next Steps ##

For details on using your Aperture Tiles visual analytic, see the [User Guide](../../../user-guide/) topic.