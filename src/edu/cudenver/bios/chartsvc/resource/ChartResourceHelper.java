/*
 * Chart Service for the GLIMMPSE Software System.  Creates
 * publishable quality scatter plots.
 * 
 * Copyright (C) 2010 Regents of the University of Colorado.  
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package edu.cudenver.bios.chartsvc.resource;

import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.StringTokenizer;

import org.restlet.data.Form;
import org.restlet.data.Status;
import org.restlet.resource.ResourceException;

import edu.cudenver.bios.chartsvc.application.ChartConstants;
import edu.cudenver.bios.chartsvc.domain.Axis;
import edu.cudenver.bios.chartsvc.domain.Chart;
import edu.cudenver.bios.chartsvc.domain.LineStyle;
import edu.cudenver.bios.chartsvc.domain.Series;

/**
 * Helper class to parse incoming chart requests
 * @author Sarah Kreidler
 *
 */
public class ChartResourceHelper
{
	private static int MAX_WIDTH = 800;
	private static int MAX_HEIGHT = 800;
	
	private enum CoordinateType
	{
		X,
		Y,
		Z
	};
	
	private enum LineStyleType
    {
        THICKNESS,
        DASH_LENGTH,
        SPACE_LENGTH
    };
	
	/**
	 * Create a chart specification from a query string.  This API mimics the 
	 * Google Chart API, with some added functionality for 3D plots
	 * 
	 * @param queryParams Form object with query parameters from the URL
	 * @param is3D if true, parse as a 3D plot, otherwise 2D
	 * @return Chart specification object
	 * @throws ResourceException
	 */
	public static Chart chartFromQueryString(Form queryParams, boolean is3D)
	throws ResourceException
	{
		Chart chart = new Chart();
		
		// get the title
		String title = queryParams.getFirstValue(ChartConstants.QPARAM_TITLE);
		if (title != null && !title.isEmpty()) chart.setTitle(title);
		
		// parse the chart dimension
		parseSize(chart, queryParams.getFirstValue(ChartConstants.QPARAM_SIZE));
		
		// parse the axis labels
		parseAxisLabels(chart, queryParams.getFirstValue(ChartConstants.QPARAM_AXIS_LABEL));

		// parse the data
		String dataString = queryParams.getFirstValue(ChartConstants.QPARAM_DATA);
				
		if (dataString != null)
		{
			if (is3D)
				parseData(chart, dataString, CoordinateType.Z);
			else
				parseData(chart, dataString, CoordinateType.Y);
		}
		
		// parse the data series labels - NOTE this needs to be called after 
        // parseData so the series objects are already created
        parseSeriesLabels(chart, queryParams.getFirstValue(ChartConstants.QPARAM_SERIES_LABEL));
        
      //parse the line style
        String lineStyleString = queryParams.getFirstValue(ChartConstants.QPARAM_LINE_STYLE);
        if(lineStyleString == null)
        {
            LineStyle lineStyle = new LineStyle();
            lineStyle.setDashLength(1.0f);
            lineStyle.setSpaceLength(1.0f);
            lineStyle.setWidth(1.0f);
            chart.addLineStyle(lineStyle);
        }
        else
        {
            parseLineStyle(chart, lineStyleString);
        }
        
        return chart;
	}
	
	private static void parseSize(Chart chart, String sizeStr)
	{
		if (sizeStr != null)
		{
			StringTokenizer st = new StringTokenizer(sizeStr, "x");
			if (st.hasMoreTokens())
			{
				int width = Integer.parseInt(st.nextToken());
				if (width > 0 && width < MAX_WIDTH)
				{
					chart.setWidth(width);
				}
			}
			if (st.hasMoreTokens())
			{
				int height = Integer.parseInt(st.nextToken());
				if (height > 0 && height < MAX_HEIGHT)
				{
					chart.setHeight(height);
				}
			}
		}
	}
	
	private static void parseData(Chart chart, String dataStr, CoordinateType maxCoordinate)
	throws ResourceException
	{
		if (dataStr != null && dataStr.startsWith("t:"))
		{
	        StringCharacterIterator iterator = new StringCharacterIterator(dataStr, 2);
	        int seriesCount = 0;
	        Series series = new Series(Integer.toString(seriesCount));

	        CoordinateType type = CoordinateType.X;
	        int baseIndex = 2;
	        while (iterator.current() != CharacterIterator.DONE) 
	        {
	            char c = iterator.current();
	            if (c >= '0' && c <= '9' || c == '-' || c == '.') 
	            {
	                iterator.next();
	            }
	            else if (c == ',') 
	            {
	            	addDataValue(series, 
	            			Double.parseDouble(dataStr.substring(baseIndex, iterator.getIndex())),
	            			type);
	                baseIndex = iterator.getIndex() + 1;
	                iterator.next();
	            }
	            else if (c == '|') 
	            {
	            	addDataValue(series, 
	            			Double.parseDouble(dataStr.substring(baseIndex, iterator.getIndex())),
	            			type);
	            	if (type == maxCoordinate)
	            	{
		                chart.addSeries(series);
		                series = new Series(Integer.toString(++seriesCount));
		                type = CoordinateType.X;
	            	}
	            	else
	            	{
	            		if (type == CoordinateType.X) 
	            			type = CoordinateType.Y;
	            		else if (type == CoordinateType.Y)
	            			type = CoordinateType.Z;
	            		else 
	            			type = CoordinateType.X;
	            	}
	                baseIndex = iterator.getIndex() + 1;
	                iterator.next();
	            }
	            else 
	            {
	                throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST,
	                		"invalid data specification (chd)");
	            }
	        }
        	addDataValue(series, 
        			Double.parseDouble(dataStr.substring(baseIndex, iterator.getIndex())),
        			type);
            chart.addSeries(series);
		}
	}

	private static void addDataValue(Series series, double value,	CoordinateType type)
	{
		switch (type)
		{
		case X:
			series.addX(value);
			break;
		case Y:
			series.addY(value);
			break;
		case Z:
			series.addZ(value);
			break;
		}
	}
	
	private static void parseSeriesLabels(Chart chart, String seriesLabels)
	{
		if (seriesLabels != null)
		{
			List<Series> seriesList = chart.getSeries();
			ListIterator<Series> seriesIterator = seriesList.listIterator();
			StringTokenizer st = new StringTokenizer(seriesLabels, ChartConstants.QPARAM_TOKEN_SEPARATOR);
			if (st.hasMoreTokens()) chart.setLegend(true);
			while (st.hasMoreTokens()) 
			{
				if (seriesIterator.hasNext())
				{
					Series currentSeries = seriesIterator.next();
					currentSeries.setLabel(st.nextToken());
				}
				else
				{
					break;
				}
			}
		}
	}
	
	private static void parseAxisLabels(Chart chart, String axisLabels)
	{
		if (axisLabels != null)
		{
			CoordinateType type = CoordinateType.X;
			StringTokenizer st = new StringTokenizer(axisLabels, ChartConstants.QPARAM_TOKEN_SEPARATOR);
			while (st.hasMoreTokens()) 
			{
				Axis axis = new Axis(st.nextToken());
				switch (type)
				{
				case X:
					chart.setXAxis(axis);
					type = CoordinateType.Y;
					break;
				case Y:
					chart.setYAxis(axis);
					type = CoordinateType.Z;
					break;
				case Z:
					chart.setYAxis(axis);
					type = CoordinateType.X;
				}
			}
		}
	}
	
	private static void parseLineStyle(Chart chart, String lineStyleString) throws
	ResourceException
	{
	    ArrayList<LineStyle> lineStyleArray = new ArrayList<LineStyle>();
	    
	    if(lineStyleString != null)
	    {
	        StringCharacterIterator iterator = new StringCharacterIterator(lineStyleString, 0);
            int baseIndex = 0;
            LineStyleType type = LineStyleType.THICKNESS;
            LineStyle lineStyle = new LineStyle();
            
            while (iterator.current() != CharacterIterator.DONE) 
            {
                char c = iterator.current();
                if (c >= '0' && c <= '9' || c == '-' || c == '.') 
                {
                    iterator.next();
                }
                else if (c == ',') 
                {
                    Double value = Double.parseDouble(lineStyleString.substring(baseIndex, iterator.getIndex()));
                    switch (type) {
                    case THICKNESS:
                        lineStyle.setWidth(value);
                        type = LineStyleType.DASH_LENGTH;
                        break;
                    case DASH_LENGTH:
                        lineStyle.setDashLength(value);
                        type = LineStyleType.SPACE_LENGTH;
                        break;
                    default:
                        throw new IllegalArgumentException("invalid line style (ls)");
                    }
                    baseIndex = iterator.getIndex() + 1;
                    iterator.next();

                } else if (c == '|') {
                    if (type == LineStyleType.SPACE_LENGTH) {
                        Double value = Double.parseDouble(lineStyleString.substring(baseIndex, iterator.getIndex()));
                        lineStyle.setSpaceLength(value);
                        lineStyleArray.add(lineStyle);
                        // start a new linestyle
                        lineStyle = new LineStyle();
                        type = LineStyleType.THICKNESS;
                    } else {
                        throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST,
                                "invalid line style specification (ls)");
                    }
                    
                    baseIndex = iterator.getIndex() + 1;
                    iterator.next();
                } else {
                    throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST,
                            "invalid line style specification (ls)");
                }
            }
            // pickup the final line style
            if (type == LineStyleType.SPACE_LENGTH) {
                Double value = Double.parseDouble(lineStyleString.substring(baseIndex, iterator.getIndex()));
                lineStyle.setSpaceLength(value);
                lineStyleArray.add(lineStyle);
            }
            
        }
	    chart.setLineStyleArray(lineStyleArray);
	    
	}
	
}
