/*
 * Copyright (c) 2014 Oculus Info Inc. http://www.oculusinfo.com/
 * 
 * Released under the MIT License.
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oculusinfo.annotation.index.impl;

import java.util.List;
import java.util.LinkedList;

import com.oculusinfo.annotation.index.*;
import com.oculusinfo.binning.*;
import com.oculusinfo.binning.impl.*;

import org.json.JSONObject;

public class FlatAnnotationIndexer extends AnnotationIndexer<JSONObject> {
	
	public static final int LEVEL_RES = 30;
    
	public FlatAnnotationIndexer() {
    	_pyramid = new WebMercatorTilePyramid();
    } 
    
    @Override
    public List<AnnotationIndex> getIndices( JSONObject data ) {
    	
    	List<AnnotationIndex> result = new LinkedList<AnnotationIndex>();
    	result.add( getIndex( data, 0 ) );
    	return result;
    	
    }
    
    @Override
    public AnnotationIndex getIndex( JSONObject data, int level ) {
    	
    	try {
	    	TileIndex tile = _pyramid.rootToTile( data.getDouble("x"),  
	    										  data.getDouble("y"), 
	    										  LEVEL_RES );
	    	
			return new AnnotationIndex( interleave( tile.getX(), tile.getY() ) );
    	} catch (Exception e) {
    		e.printStackTrace();
    	}
    	return null;
    }

    
    private long interleave( long x, long y) {
    	   	
    	x = (x | (x << SHIFTS[4])) & BITS[4];
        x = (x | (x << SHIFTS[3])) & BITS[3];
        x = (x | (x << SHIFTS[2])) & BITS[2];
        x = (x | (x << SHIFTS[1])) & BITS[1];
        x = (x | (x << SHIFTS[0])) & BITS[0];

        y = (y | (y << SHIFTS[4])) & BITS[4];
        y = (y | (y << SHIFTS[3])) & BITS[3];
        y = (y | (y << SHIFTS[2])) & BITS[2];
        y = (y | (y << SHIFTS[1])) & BITS[1];
        y = (y | (y << SHIFTS[0])) & BITS[0];

        return x | (y << 1);
    }

    
}
