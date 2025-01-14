/*-
 * #%L
 * Mastodon
 * %%
 * Copyright (C) 2014 - 2024 Tobias Pietzsch, Jean-Yves Tinevez
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package org.mastodon.mamut;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.WordUtils;
import org.mastodon.mamut.model.Model;
import org.mastodon.mamut.model.ModelGraph;
import org.mastodon.tracking.mamut.detection.DetectionQualityFeature;
import org.mastodon.tracking.mamut.detection.SpotDetectorOp;
import org.mastodon.tracking.mamut.linking.LinkCostFeature;
import org.mastodon.tracking.mamut.linking.SpotLinkerOp;
import org.mastodon.tracking.mamut.trackmate.PluginProvider;
import org.mastodon.tracking.mamut.trackmate.TrackMate;
import org.scijava.log.Logger;

import bdv.viewer.SourceAndConverter;
import net.imagej.ops.OpService;
import net.imagej.ops.special.hybrid.Hybrids;
import net.imagej.ops.special.hybrid.UnaryHybridCF;
import net.imagej.ops.special.inplace.Inplaces;

/**
 * The tracking gateway used in scripting to configure and execute tracking in
 * Mastodon scripts.
 * 
 * @author Jean-Yves Tinevez
 *
 */
public class TrackMateProxy
{
	final TrackMate trackmate;

	private final Logger logger;

	TrackMateProxy( final TrackMate trackmate, final Logger logger )
	{
		this.trackmate = trackmate;
		this.logger = logger;
	}

	/**
	 * Configures this tracking session to use the specified detector. Prints an
	 * error message if the name is unknown.
	 * 
	 * @param detector
	 *            the name of the detector, as returned in
	 *            {@link #infoDetectors()}.
	 */
	public void useDetector( final String detector )
	{
		final PluginProvider< SpotDetectorOp > detectorprovider = new PluginProvider<>( SpotDetectorOp.class );
		trackmate.context().inject( detectorprovider );
		final List< String > detectorNames = detectorprovider.getNames();
		final int indexOf = detectorNames.indexOf( detector );
		if ( indexOf < 0 )
		{
			logger.error( "Unknown detector: " + detector + ".\n" );
			return;
		}

		final Class< ? extends SpotDetectorOp > detectorClass = detectorprovider.getClasses().get( indexOf );
		trackmate.getSettings().detector( detectorClass );

		final Map< String, Object > oldSettings = new HashMap<>( trackmate.getSettings().values.getDetectorSettings() );
		final Map< String, Object > newSettings = getDefaultDetectorSettings( detectorClass );
		// Copy as much as we can from old to new.
		for ( final String key : newSettings.keySet() )
		{
			final Object oldVal = oldSettings.get( key );
			if ( oldVal != null )
				newSettings.put( key, oldVal );
		}
		trackmate.getSettings().detectorSettings( newSettings );
	}

	/**
	 * Resets the detection settings to their default values.
	 */
	public void resetDetectorSettings()
	{
		final Map< String, Object > dSettings = getDefaultDetectorSettings( trackmate.getSettings().values.getDetector() );
		trackmate.getSettings().detectorSettings( dSettings );
	}

	private Map< String, Object > getDefaultDetectorSettings( final Class< ? extends SpotDetectorOp > detectorClass )
	{
		// Instantiate a dummy detector.
		final Model model = trackmate.getModel();
		final ModelGraph graph = model.getGraph();
		final DetectionQualityFeature qualityFeature = DetectionQualityFeature.getOrRegister(
				model.getFeatureModel(), graph.vertices().getRefPool() );
		final OpService ops = trackmate.getContext().getService( OpService.class );
		final UnaryHybridCF< List< SourceAndConverter< ? > >, ModelGraph > unaryCF = Hybrids.unaryCF( ops, detectorClass,
				graph, trackmate.getSettings().values.getSources(),
				new HashMap< String, Object >(),
				model.getSpatioTemporalIndex(),
				qualityFeature );
		final SpotDetectorOp detector = ( SpotDetectorOp ) unaryCF;
		return detector.getDefaultSettings();
	}

	/**
	 * Configures this tracking session to use the specified linker. Prints an
	 * error message if the name is unknown.
	 * 
	 * @param linker
	 *            the name of the linker, as returned in {@link #infoLinkers()}.
	 */
	public void useLinker( final String linker )
	{
		final PluginProvider< SpotLinkerOp > linkerprovider = new PluginProvider<>( SpotLinkerOp.class );
		trackmate.context().inject( linkerprovider );
		final List< String > linkerNames = linkerprovider.getNames();
		final int indexOf = linkerNames.indexOf( linker );
		if ( indexOf < 0 )
		{
			logger.error( "Unknown linker: " + linker + ".\n" );
			return;
		}

		final Class< ? extends SpotLinkerOp > linkerClass = linkerprovider.getClasses().get( indexOf );
		trackmate.getSettings().linker( linkerClass );

		final Map< String, Object > oldSettings = new HashMap<>( trackmate.getSettings().values.getLinkerSettings() );
		final Map< String, Object > newSettings = getDefaultLinkerSettings( linkerClass );
		// Copy as much as we can from old to new.
		for ( final String key : newSettings.keySet() )
		{
			final Object oldVal = oldSettings.get( key );
			if ( oldVal != null )
				newSettings.put( key, oldVal );
		}
		trackmate.getSettings().linkerSettings( newSettings );
	}

	/**
	 * Resets the linking settings to their default values.
	 */
	public void resetLinkerSettings()
	{
		final Map< String, Object > lSettings = getDefaultLinkerSettings( trackmate.getSettings().values.getLinker() );
		trackmate.getSettings().linkerSettings( lSettings );
	}

	private Map< String, Object > getDefaultLinkerSettings( final Class< ? extends SpotLinkerOp > linkerCl )
	{
		// Instantiate a dummy detector.
		final Model model = trackmate.getModel();
		final LinkCostFeature linkCostFeature = LinkCostFeature.getOrRegister(
				model.getFeatureModel(), model.getGraph().edges().getRefPool() );
		final OpService ops = trackmate.getContext().getService( OpService.class );

		final SpotLinkerOp linker =
				( SpotLinkerOp ) Inplaces.binary1( ops, linkerCl, model.getGraph(),
						model.getSpatioTemporalIndex(),
						new HashMap< String, Object >(),
						model.getFeatureModel(),
						linkCostFeature );
		return linker.getDefaultSettings();
	}

	/**
	 * Configures one parameter of the current detector. The parameter key and
	 * value must be valid for the detector set with
	 * {@link #useDetector(String)}, as shown in {@link #infoDetectors()}.
	 * 
	 * @param key
	 *            the key of the parameter.
	 * @param value
	 *            the value to set for this parameter.
	 */
	public void setDetectorSetting( final String key, final Object value )
	{
		if ( !trackmate.getSettings().values.getDetectorSettings().containsKey( key ) )
		{
			logger.error( "Unknown parameter " + key + " for detector " + trackmate.getSettings().values.getDetector().getName() + '\n' );
			return;
		}
		// Check expected type of value.
		final Object oldValue = trackmate.getSettings().values.getDetectorSettings().get( key );
		if ( oldValue != null && !oldValue.getClass().isAssignableFrom( value.getClass() ) )
		{
			logger.error( "Incorrect value type for parameter " + key + ". Expected " + oldValue.getClass() + " but got " + value.getClass() + '\n' );
			return;
		}
		trackmate.getSettings().values.getDetectorSettings().put( key, value );
	}

	/**
	 * Configures one parameter of the current link. The parameter key and value
	 * must be valid for the link set with {@link #useLinker(String)}, as shown
	 * in {@link #infoLinkers()}.
	 * 
	 * @param key
	 *            the key of the parameter.
	 * @param value
	 *            the value to set for this parameter.
	 */
	public void setLinkerSetting( final String key, final Object value )
	{
		if ( !trackmate.getSettings().values.getLinkerSettings().containsKey( key ) )
		{
			logger.error( "Unknown parameter " + key + " for linker " + trackmate.getSettings().values.getLinker().getName() + '\n' );
			return;
		}
		// Check expected type of value.
		final Object oldValue = trackmate.getSettings().values.getLinkerSettings().get( key );
		if ( oldValue != null && !oldValue.getClass().isAssignableFrom( value.getClass() ) )
		{
			logger.error( "Incorrect value type for parameter " + key + ". Expected " + oldValue.getClass() + " but got " + value.getClass() + '\n' );
			return;
		}
		trackmate.getSettings().values.getLinkerSettings().put( key, value );
	}

	/**
	 * Executes the tracking with current configuration.
	 * 
	 * @return <code>true</code> if tracking completed successful. An error
	 *         message will be printed otherwise.
	 */
	public boolean run()
	{
		trackmate.run();

		if ( trackmate.isCanceled() )
			logger.warn( "Canceled: " + trackmate.getCancelReason() );

		if ( !trackmate.isSuccessful() )
			logger.error( trackmate.getErrorMessage() );

		return trackmate.isSuccessful();
	}

	/**
	 * Prints the current tracking configuration.
	 */
	public void info()
	{
		logger.info( "TrackMate settings:\n" + trackmate.getSettings().toString() + '\n' );
	}

	/**
	 * Prints information on the collection of detectors currently usable in
	 * Mastodon.
	 */
	public void infoDetectors()
	{
		final StringBuilder str = new StringBuilder();
		str.append( "Available detectors:\n" );

		final PluginProvider< SpotDetectorOp > detectorprovider = new PluginProvider<>( SpotDetectorOp.class );
		trackmate.context().inject( detectorprovider );
		final List< String > detectorNames = detectorprovider.getNames();
		for ( int i = 0; i < detectorNames.size(); i++ )
		{
			final String detectorName = detectorNames.get( i );
			final String headerStr = String.format( "\n%2d: '%s'\n", i, detectorName );
			str.append( headerStr );
			str.append( line( headerStr.length() ) + '\n' );

			str.append( "Description:\n" );
			final String wrapped = wrap( htmlToText( detectorprovider.getDescriptions().get( detectorName ) ), 70, "\n    " );
			str.append( "    " + wrapped + '\n' );

			str.append( "Parameters:\n" );
			final String settingsParamLine = "    %-40s %-20s %-20s\n";
			str.append( String.format( settingsParamLine, "Name", "Type", "Default value" ) );
			str.append( String.format( settingsParamLine, line( "Name".length() ), line( "Type".length() ), line( "Default value".length() ) ) );
			final Map< String, Object > settings = getDefaultDetectorSettings( detectorprovider.getClasses().get( i ) );
			final List< String > keys = new ArrayList<>( settings.keySet() );
			keys.sort( null );
			for ( final String key : keys )
			{
				final Object val = settings.get( key );
				final String typeStr = ( val == null ) ? defaultParamType( key ) : val.getClass().getSimpleName();
				str.append( String.format( settingsParamLine, key, typeStr, val ) );
			}
		}

		logger.info( str );
	}

	/**
	 * Prints information on the collection of linkers currently usable in
	 * Mastodon.
	 */
	public void infoLinkers()
	{
		final StringBuilder str = new StringBuilder();
		str.append( "Available linkers:\n" );

		final PluginProvider< SpotLinkerOp > linkerprovider = new PluginProvider<>( SpotLinkerOp.class );
		trackmate.context().inject( linkerprovider );
		final List< String > linkerNames = linkerprovider.getNames();
		for ( int i = 0; i < linkerNames.size(); i++ )
		{
			final String linkerName = linkerNames.get( i );
			final String headerStr = String.format( "\n%2d: '%s'\n", i, linkerName );
			str.append( headerStr );
			str.append( line( headerStr.length() ) + '\n' );

			str.append( "Description:\n" );
			final String wrapped = wrap( htmlToText( linkerprovider.getDescriptions().get( linkerName ) ), 70, "\n    " );
			str.append( "    " + wrapped + '\n' );

			str.append( "Parameters:\n" );
			final String settingsParamLine = "    %-40s %-20s %-20s\n";
			str.append( String.format( settingsParamLine, "Name", "Type", "Default value" ) );
			str.append( String.format( settingsParamLine, line( "Name".length() ), line( "Type".length() ), line( "Default value".length() ) ) );
			final Map< String, Object > settings = getDefaultLinkerSettings( linkerprovider.getClasses().get( i ) );
			final List< String > keys = new ArrayList<>( settings.keySet() );
			keys.sort( null );
			for ( final String key : keys )
			{
				final Object val = settings.get( key );
				final String typeStr = ( val == null ) ? defaultParamType( key ) : val.getClass().getSimpleName();
				str.append( String.format( settingsParamLine, key, typeStr, val ) );
			}
		}

		logger.info( str );
	}

	private static String defaultParamType( final String param )
	{
		switch ( param )
		{
		default:
			return "?";
		case "ADD_BEHAVIOR":
		case "DETECTION_TYPE":
			return "String";
		case "ROI":
			return "Interval";
		}
	}

	private static String wrap( final String str, final int wrapLength, final String newLineStr )
	{
		return WordUtils.wrap( str, wrapLength, newLineStr, false );
	}

	private static String line( final int length )
	{
		final char[] line = new char[ length ];
		Arrays.fill( line, '-' );
		return new String( line );
	}

	private static String htmlToText( final String htmlText )
	{
		return htmlText.replaceAll( "(?s)<[^>]*>(\\s*<[^>]*>)*", " " );
	}
}
