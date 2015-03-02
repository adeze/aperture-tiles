/*
 * Copyright (c) 2014 Oculus Info Inc.
 * http://www.oculusinfo.com/
 *
 * Released under the MIT License.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do
 * so, subject to the following conditions:

 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.

 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

( function() {

    "use strict";

    var Util = require('../util/Util'),
        Layer = require('./Layer'),
        LayerUtil = require('./LayerUtil'),
        PubSub = require('../util/PubSub'),
        LegendService = require('../rest/LegendService'),
        setRampImageUrl,
        setLevelMinMax,
        zoomCallback;

    /**
     * Private: Request colour ramp image from server and set layer property when received.
     *
     * @param {Object} layer - The layer object
     * @param {Function} callback - Optional callback function.
     */
    setRampImageUrl = function( layer, callback ) {
        LegendService.getEncodedImage( layer.source.id, {
                renderer: layer.renderer
            }, function ( url ) {
                layer.rampImageUrl = url;
                PubSub.publish( layer.getChannel(), { field: 'rampImageUrl', value: url });
                if ( callback ) {
                    callback( url );
                }
            });
    };

    /**
     * Private: Sets the layers min and max values for the given zoom level.
     *
     * @param layer {Object} the layer object.
     */
    setLevelMinMax = function( layer ) {
        var zoomLevel = layer.map.getZoom(),
            coarseness = layer.renderer.coarseness,
            adjustedZoom = Math.max( zoomLevel - ( coarseness-1 ), 0 ),
            meta =  layer.source.meta,
            levelMinMax = meta.minMax[ adjustedZoom ],
            minMax = levelMinMax ? levelMinMax.minMax : {
                min: null,
                max: null
            };
        layer.levelMinMax = minMax;
        PubSub.publish( layer.getChannel(), { field: 'levelMinMax', value: minMax });
    };

    /**
     * Private: Returns the zoom callback function to update level min and maxes.
     *
     * @param layer {ServerLayer} The layer object.
     */
    zoomCallback = function( layer ) {
        return function() {
            if ( layer.olLayer ) {
                setLevelMinMax( layer );
            }
        };
    };

    /**
     * Instantiate a ServerLayer object.
     * @class ServerLayer
     * @classdesc A server rendered image layer that displays images retrieved from the server.
     *            Respective server side rendering parameters may be modified using the interface
     *            of the object.
     *
     * @param spec {Object} The Specification object.
     * <pre>
     * {
     *     opacity {float}   - The opacity of the layer. Default = 1.0.
     *     enabled {boolean} - Whether the layer is visible or not. Default = true.
     *     zIndex  {integer} - The z index of the layer. Default = 1.
     *     renderer: {
     *         coarseness {integer} - The pixel by pixel coarseness. Default based on server configuration.
     *         ramp       {String}  - The color ramp type. Default based on server configuration.
     *         rangeMin   {integer} - The minimum percentage to clamp the low end of the color ramp. Default based on server configuration.
     *         rangeMax   {integer} - The maximum percentage to clamp the high end of the color ramp. Default based on server configuration.
     *     },
     *     valueTransform: {
     *         type {String} - Value transformer type. Default based on server configuration.
     *     },
     *     tileTransform: {
     *         type {String} - Tile transformer type. Default based on server configuration.
     *         data {Object} - The tile transformer data initialization object. Default based on server configuration.
     *     }
     * }
     * </pre>
     */
    function ServerLayer( spec ) {
        // call base constructor
        Layer.call( this, spec );
        // set reasonable defaults
        this.zIndex = ( spec.zIndex !== undefined ) ? spec.zIndex : 1;
        this.renderer = spec.renderer || {};
        this.renderer.coarseness = ( this.renderer.coarseness !== undefined ) ? this.renderer.coarseness : 1;
        this.renderer.rangeMin = ( this.renderer.rangeMin !== undefined ) ? this.renderer.rangeMin : 0;
        this.renderer.rangeMax = ( this.renderer.rangeMax !== undefined ) ? this.renderer.rangeMax : 100;
        this.valueTransform = spec.valueTransform || {};
        this.tileTransform = spec.tileTransform || {};
        this.domain = "server";
        this.source = spec.source;
    }

    ServerLayer.prototype = Object.create( Layer.prototype );

    /**
     * Activates the layer object. This should never be called manually.
     * @memberof ServerLayer
     * @private
     */
    ServerLayer.prototype.activate = function() {

        var that = this;
        // set callback here so it can be removed later
        this.zoomCallback = zoomCallback( this );
        // set callback to update ramp min/max on zoom
        this.map.on( "zoomend", this.zoomCallback );

        function getURL( bounds ) {
            return LayerUtil.getURL.call( this, bounds ) + that.getQueryParamString();
        }

        // add the new layer
        this.olLayer = new OpenLayers.Layer.TMS(
            'Server Rendered Tile Layer',
            this.source.tms,
            {
                layername: this.source.id,
                type: 'png',
                maxExtent: new OpenLayers.Bounds(-20037500, -20037500,
                    20037500,  20037500),
                transparent: true,
                isBaseLayer: false,
                getURL: getURL
            });
        this.map.olMap.addLayer( this.olLayer );

        this.setZIndex( this.zIndex );
        this.setOpacity( this.opacity );
        this.setEnabled( this.enabled );
        this.setTheme( this.map.getTheme() ); // sends initial request for ramp image
        setLevelMinMax( this );
    };

    /**
     * Dectivates the layer object. This should never be called manually.
     * @memberof ServerLayer
     * @private
     */
    ServerLayer.prototype.deactivate = function() {
        if ( this.olLayer ) {
            this.map.olMap.removeLayer( this.olLayer );
            this.olLayer.destroy();
            this.olLayer = null;
        }
        this.map.off( "zoomend", this.zoomCallback );
        this.zoomCallback = null;
    };

    /**
     * Updates the theme associated with the layer.
     * @memberof ServerLayer
     *
     * @param {String} theme - The theme identifier string.
     */
    ServerLayer.prototype.setTheme = function( theme ) {
        var that = this;
        this.renderer.theme = theme;
        setRampImageUrl( that );
        this.olLayer.redraw();
    };

    /**
     * Get the current theme for the layer.
     * @memberof ServerLayer
     *
     * @returns {String} The theme identifier string.
     */
    ServerLayer.prototype.getTheme = function() {
        return this.renderer.theme;
    };

    /**
     * Set the z index of the layer.
     * @memberof ServerLayer
     *
     * @param {integer} zIndex - The new z-order value of the layer, where 0 is front.
     */
    ServerLayer.prototype.setZIndex = function ( zIndex ) {
        // we by-pass the OpenLayers.Map.setLayerIndex() method and manually
        // set the z-index of the layer dev. setLayerIndex sets a relative
        // index based on current map layers, which then sets a z-index. This
        // caused issues with async layer loading.
        this.zIndex = zIndex;
        if ( this.olLayer ) {
            $( this.olLayer.div ).css( 'z-index', zIndex );
            PubSub.publish( this.getChannel(), { field: 'zIndex', value: zIndex });
        }
    };

    /**
     * Get the layers zIndex.
     * @memberof ServerLayer
     *
     * @returns {integer} The zIndex for the layer.
     */
    ServerLayer.prototype.getZIndex = function () {
        return this.zIndex;
    };

    /**
     * Set the ramp type associated with the layer.
     * @memberof ServerLayer
     *
     * @param {String} rampType - The ramp type used to render the images.
     * @param {Function} callback - Optional callback function.
     */
    ServerLayer.prototype.setRampType = function ( rampType, callback ) {
        if ( this.renderer.ramp !== rampType ) {
            this.renderer.ramp = rampType;
            setRampImageUrl( this, callback );
            this.olLayer.redraw();
            PubSub.publish( this.getChannel(), {field: 'rampType', value: rampType} );
        }
    };

    /**
     * Get ramp type for layer.
     * @memberof ServerLayer
     *
     * @returns {String} The ramp identification string.
     */
    ServerLayer.prototype.getRampType = function() {
        return this.renderer.ramp;
    };

    /**
     * Get the current minimum and maximum values for the current zoom level.
     * @memberof ServerLayer
     *
     * @param {Object} The min and max of the level.
     */
    ServerLayer.prototype.getLevelMinMax = function() {
        return this.levelMinMax;
    };

    /**
     * Get the current ramp image URL string.
     * @memberof ServerLayer
     *
     * @returns {String} The encoded ramp image url.
     */
    ServerLayer.prototype.getRampImageUrl = function() {
        return this.rampImageUrl;
    };

    /**
     * Set the current value by which the minimum color ramp is clamped to by percentage
     * in the range [0-1].
     * @memberof ServerLayer
     *
     * @param {number} min - The range min in percentage.
     */
    ServerLayer.prototype.setRangeMinPercentage = function( min ) {
        min = Math.max( Math.min( min, 1 ), 0 ) * 100;
        if ( this.renderer.rangeMin !== min ) {
            this.renderer.rangeMin = min;
            this.olLayer.redraw();
            PubSub.publish( this.getChannel(), { field: 'rangeMin', value: min });
        }
    };

    /**
     * Get the current value by which the minimum color ramp is clamped to by percentage
     * in the range [0-1].
     * @memberof ServerLayer
     *
     * @returns {number} The range max in percentage.
     */
    ServerLayer.prototype.getRangeMinPercentage = function() {
        return this.renderer.rangeMin / 100;
    };

    /**
     * Set the current value by which the maximum color ramp is clamped to by percentage
     * in the range [0-1].
     * @memberof ServerLayer
     *
     * @param {number} max - The range max in percentage.
     */
    ServerLayer.prototype.setRangeMaxPercentage = function( max ) {
        max = Math.max( Math.min( max, 1 ), 0 ) * 100;
        if ( this.renderer.rangeMax !== max ) {
            this.renderer.rangeMax = max;
            this.olLayer.redraw();
            PubSub.publish( this.getChannel(), {field: 'rangeMax', value: max} );
        }
    };

    /**
     * Get the current value by which the maximum color ramp is clamped to by percentage
     * in the range [0-1].
     * @memberof ServerLayer
     *
     * @returns {number} The range min in percentage.
     */
    ServerLayer.prototype.getRangeMaxPercentage = function() {
        return this.renderer.rangeMax / 100;
    };

    /**
    * Set the current value by which the minimum color ramp is clamped to.
    * @memberof ServerLayer
    *
    * @param {number} min - The range min by value.
    */
    ServerLayer.prototype.setRangeMinValue = function( min ) {
        this.setRangeMinPercentage(
            Util.normalizeValue(
                min,
                this.getLevelMinMax(),
                this.getValueTransformType()
            )
        );
    };

    /**
    * Get the current value by which the minimum color ramp is clamped to.
    * @memberof ServerLayer
    *
    * @returns {number} The range min by value.
    */
    ServerLayer.prototype.getRangeMinValue = function() {
        return Util.denormalizeValue(
            this.getRangeMinPercentage(),
            this.getLevelMinMax(),
            this.getValueTransformType() );
    };

    /**
    * Set the current value by which the maximum color ramp is clamped to.
    * @memberof ServerLayer
    *
    * @param {number} max - The range max by value.
    */
    ServerLayer.prototype.setRangeMaxValue = function( max ) {
        this.setRangeMaxPercentage(
            Util.normalizeValue(
                max,
                this.getLevelMinMax(),
                this.getValueTransformType()
            )
        );
    };

    /**
    * Get the current value by which the maximum color ramp is clamped to.
    * @memberof ServerLayer
    *
    * @returns {number} The range max by value.
    */
    ServerLayer.prototype.getRangeMaxValue = function() {
        return Util.denormalizeValue(
            this.getRangeMaxPercentage(),
            this.getLevelMinMax(),
            this.getValueTransformType() );
    };

    /**
     * Updates the value transform function associated with the layer. Results in a POST
     * request to the server.
     * @memberof ServerLayer
     *
     * @param {String} transformType - The new new ramp function.
     */
    ServerLayer.prototype.setValueTransformType = function ( transformType ) {
        if ( this.valueTransform.type !== transformType ) {
            this.valueTransform.type = transformType;
            this.olLayer.redraw();
            PubSub.publish( this.getChannel(), { field: 'valueTransformType', value: transformType });
        }
    };

    /**
     * Get the current value transform function type.
     * @memberof ServerLayer
     *
     * @return {String} The value transform type.
     */
    ServerLayer.prototype.getValueTransformType = function() {
        return this.valueTransform.type;
    };

    /**
     * Set the layers tile transform function type.
     * @memberof ServerLayer
     *
     * @param {String} transformType - The tile transformer type.
     */
    ServerLayer.prototype.setTileTransformType = function ( transformType ) {
        if ( this.tileTransform.type !== transformType ) {
            this.tileTransform.type = transformType;
            this.olLayer.redraw();
            PubSub.publish( this.getChannel(), {field: 'tileTransformType', value: transformType} );
        }
    };

    /**
     * Get the layers transformer type.
     * @memberof ServerLayer
     *
     * @return {String} The tile transform type.
     */
    ServerLayer.prototype.getTileTransformType = function () {
        return this.tileTransform.type;
    };

    /**
     * Set the tile transform data attribute
     * @memberof ServerLayer
     *
     * @param {Object} transformData - The tile transform data attribute.
     */
    ServerLayer.prototype.setTileTransformData = function ( transformData ) {
        if ( this.tileTransform.data !== transformData ) {
            this.tileTransform.data = transformData;
            this.olLayer.redraw();
            PubSub.publish( this.getChannel(), {field: 'tileTransformData', value: transformData} );
        }
    };

    /**
     * Get the transformer data attribute.
     * @memberof ServerLayer
     *
     * @returns {Object} The tile transform data attribute.
     */
    ServerLayer.prototype.getTileTransformData = function () {
        return this.tileTransform.data;
    };

    /**
     * Set the layers pixel coarseness.
     * @memberof ServerLayer
     *
     * @param coarseness {integer} The pixel by pixel coarseness of the layer
     */
    ServerLayer.prototype.setCoarseness = function( coarseness ) {
        if ( this.renderer.coarseness !== coarseness ) {
            this.renderer.coarseness = coarseness;
            setLevelMinMax( this ); // coarseness modifies the min/max
            this.olLayer.redraw();
            PubSub.publish( this.getChannel(), { field: 'coarseness', value: coarseness });
        }
    };

    /**
     * Get the layers pixel coarseness.
     * @memberof ServerLayer
     *
     * @returns {integer} The layers coarseness in N by N pixels.
     */
    ServerLayer.prototype.getCoarseness = function() {
        return this.renderer.coarseness;
    };

    /**
     * Generate query parameters based on state of layer
     * @memberof ServerLayer
     *
     * @returns {String} The query parameter string based on the attributes of this layer.
     */
    ServerLayer.prototype.getQueryParamString = function() {
        var query = {
            renderer: this.renderer,
            tileTransform: this.tileTransform,
            valueTransform: this.valueTransform
        };
        return Util.encodeQueryParams( query );
    };

    /**
     * Redraws the entire layer.
     * @memberof ServerLayer
     */
    ServerLayer.prototype.redraw = function () {
        if ( this.olLayer ) {
             this.olLayer.redraw();
        }
    };

    module.exports = ServerLayer;
}());
