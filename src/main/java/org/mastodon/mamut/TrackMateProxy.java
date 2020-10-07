package org.mastodon.mamut;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.WordUtils;
import org.mastodon.tracking.detection.DetectionUtil;
import org.mastodon.tracking.linking.LinkingUtils;
import org.mastodon.tracking.mamut.detection.SpotDetectorOp;
import org.mastodon.tracking.mamut.linking.KalmanLinkerMamut;
import org.mastodon.tracking.mamut.linking.SpotLinkerOp;
import org.mastodon.tracking.mamut.trackmate.PluginProvider;
import org.mastodon.tracking.mamut.trackmate.TrackMate;
import org.scijava.log.Logger;

public class TrackMateProxy
{
	final TrackMate trackmate;

	private final Logger logger;

	TrackMateProxy( final TrackMate trackmate, final Logger logger )
	{
		this.trackmate = trackmate;
		this.logger = logger;
	}

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
		final Map< String, Object > newSettings = getDefaultDetectorSettings( detectorClass.getName() );
		// Copy as much as we can from old to new.
		for ( final String key : newSettings.keySet() )
		{
			final Object oldVal = oldSettings.get( key );
			if ( oldVal != null )
				newSettings.put( key, oldVal );
		}
		trackmate.getSettings().detectorSettings( newSettings );
	}

	public void resetDetectorSettings()
	{
		final Map< String, Object > dSettings = getDefaultDetectorSettings( trackmate.getSettings().values.getDetector().getName() );
		trackmate.getSettings().detectorSettings( dSettings );
	}

	private Map< String, Object > getDefaultDetectorSettings( final String className )
	{
		switch ( className )
		{
		default:
		case "org.mastodon.tracking.mamut.detection.DoGDetectorMamut":
		case "org.mastodon.tracking.mamut.detection.AdvancedDoGDetectorMamut":
		case "org.mastodon.tracking.mamut.detection.LoGDetectorOp":
			return DetectionUtil.getDefaultDetectorSettingsMap();
		}
	}

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
		final Map< String, Object > newSettings = getDefaultLinkerSettings( linkerClass.getName() );
		// Copy as much as we can from old to new.
		for ( final String key : newSettings.keySet() )
		{
			final Object oldVal = oldSettings.get( key );
			if ( oldVal != null )
				newSettings.put( key, oldVal );
		}
		trackmate.getSettings().linkerSettings( newSettings );
	}

	public void resetLinkerSettings()
	{
		final Map< String, Object > lSettings = getDefaultLinkerSettings( trackmate.getSettings().values.getLinker().getName() );
		trackmate.getSettings().linkerSettings( lSettings );
	}

	private Map< String, Object > getDefaultLinkerSettings( final String className )
	{
		switch ( className )
		{
		default:
		case "org.mastodon.tracking.mamut.linking.SparseLAPLinkerMamut":
		case "org.mastodon.tracking.mamut.linking.SimpleSparseLAPLinkerMamut":
			return LinkingUtils.getDefaultLAPSettingsMap();
		case "org.mastodon.tracking.mamut.linking.KalmanLinkerMamut":
			return KalmanLinkerMamut.getDefaultSettingsMap();
		}
	}

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

	public boolean run()
	{
		trackmate.run();

		if ( trackmate.isCanceled() )
			logger.warn( "Canceled: " + trackmate.getCancelReason() );

		if ( !trackmate.isSuccessful() )
			logger.error( trackmate.getErrorMessage() );

		return trackmate.isSuccessful();
	}

	public void info()
	{
		logger.info( "TrackMate settings:\n" + trackmate.getSettings().toString() + '\n' );
	}

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
			final Map< String, Object > settings = getDefaultDetectorSettings( detectorprovider.getClasses().get( i ).getName() );
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
			final Map< String, Object > settings = getDefaultLinkerSettings( linkerprovider.getClasses().get( i ).getName() );
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
