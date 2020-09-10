package org.mastodon.mamut;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import org.mastodon.feature.Feature;
import org.mastodon.feature.FeatureModel;
import org.mastodon.feature.FeatureSpec;
import org.mastodon.mamut.feature.MamutFeatureComputerService;
import org.mastodon.mamut.model.Link;
import org.mastodon.mamut.model.Model;
import org.mastodon.mamut.model.Spot;
import org.mastodon.mamut.project.MamutProject;
import org.mastodon.mamut.project.MamutProjectIO;
import org.mastodon.model.SelectionModel;
import org.mastodon.tracking.detection.DetectionUtil;
import org.mastodon.tracking.detection.DetectorKeys;
import org.mastodon.tracking.linking.LinkingUtils;
import org.mastodon.tracking.mamut.detection.DoGDetectorMamut;
import org.mastodon.tracking.mamut.linking.SimpleSparseLAPLinkerMamut;
import org.mastodon.tracking.mamut.trackmate.Settings;
import org.mastodon.tracking.mamut.trackmate.TrackMate;
import org.mastodon.views.bdv.SharedBigDataViewerData;
import org.scijava.Context;
import org.scijava.log.Logger;
import org.scijava.log.StderrLogService;

import mpicbg.spim.data.SpimDataException;

public class Mamut
{

	private final WindowManager wm;

	private final MamutFeatureComputerService featureComputerService;

	private Logger logger = new StderrLogService();

	private Mamut( final WindowManager wm )
	{
		this.wm = wm;
		this.featureComputerService = wm.getContext().getService( MamutFeatureComputerService.class );
		featureComputerService.setModel( wm.getAppModel().getModel() );
		featureComputerService.setSharedBdvData( wm.getAppModel().getSharedBdvData() );

	}

	public static final Mamut open( final String mamutProject ) throws IOException, SpimDataException
	{
		return open( mamutProject, new Context() );
	}

	public static final Mamut open( final String mamutProject, final Context context ) throws IOException, SpimDataException
	{
		final MamutProject project = new MamutProjectIO().load( mamutProject );
		final WindowManager wm = new WindowManager( context );
		wm.getProjectManager().open( project );
		return new Mamut( wm );
	}

	public static final Mamut newProject( final String bdvFile, final Context context ) throws IOException, SpimDataException
	{
		final WindowManager wm = new WindowManager( context );
		final MamutProject project = new MamutProject( null, new File( bdvFile ) );
		wm.getProjectManager().open( project );
		return new Mamut( wm );
	}

	public static final Mamut newProject( final String bdvFile ) throws IOException, SpimDataException
	{
		return newProject( bdvFile, new Context() );
	}

	/*
	 * Instance method.
	 */

	public void setLogger( final Logger logger )
	{
		this.logger = logger;
	}

	public Model getModel()
	{
		return wm.getAppModel().getModel();
	}

	public SelectionModel< Spot, Link > getSelectionModel()
	{
		return wm.getAppModel().getSelectionModel();
	}

	public WindowManager getWindowManager()
	{
		return wm;
	}

	public TrackMate createTrackMate()
	{
		final SharedBigDataViewerData imageData = wm.getAppModel().getSharedBdvData();
		final int numTimepoints = imageData.getNumTimepoints();

		final Map< String, Object > detectorSettings = DetectionUtil.getDefaultDetectorSettingsMap();
		detectorSettings.put( DetectorKeys.KEY_MAX_TIMEPOINT, numTimepoints );

		final Map< String, Object > linkerSettings = LinkingUtils.getDefaultLAPSettingsMap();
		linkerSettings.put( DetectorKeys.KEY_MAX_TIMEPOINT, numTimepoints );

		final Settings settings = new Settings()
				.sources( imageData.getSources() )
				.detector( DoGDetectorMamut.class )
				.detectorSettings( detectorSettings )
				.linker( SimpleSparseLAPLinkerMamut.class )
				.linkerSettings( linkerSettings );

		final TrackMate trackmate = new TrackMate( settings, getModel(), getSelectionModel() );
		trackmate.setContext( wm.getContext() );
		trackmate.setLogger( logger );
		return trackmate;
	}

	public void computeFeatures( final String... featureKeys )
	{
		computeFeatures( false, featureKeys );
	}

	public void computeFeatures( final boolean forceComputeAll, final String... featureKeys )
	{
		logger.info( "Feature computation started." );
		final Collection< FeatureSpec< ?, ? > > featureSpecs = new ArrayList<>();
		for ( final String key : featureKeys )
		{
			final FeatureSpec< ?, ? > spec = wm.getFeatureSpecsService().getSpec( key );
			if ( null == spec )
			{
				logger.warn( "Could not find a feature corresponding to the key " + key + ". Skipping." );
				continue;
			}

			if ( spec.getTargetClass().equals( Spot.class ) || spec.getTargetClass().equals( Link.class ) )
				featureSpecs.add( spec );
			else
				logger.warn( "The feature " + key + " is defined for " + spec.getTargetClass() + " objects, not for spots or links. Skipping." );
		}

		final Map< FeatureSpec< ?, ? >, Feature< ? > > map = featureComputerService.compute( forceComputeAll, featureSpecs );
		if ( featureComputerService.isCanceled() )
		{
			logger.warn( "Feature computation canceled. Reason: " + featureComputerService.getCancelReason() );
			return;
		}

		final FeatureModel featureModel = getModel().getFeatureModel();
		featureModel.pauseListeners();
		// Clear feature we can compute
		final Collection< FeatureSpec< ?, ? > > featureSpecsIn = featureModel.getFeatureSpecs();
		final Collection< FeatureSpec< ?, ? > > toClear = new ArrayList<>();
		for ( final FeatureSpec< ?, ? > featureSpec : featureSpecsIn )
			if ( null != featureComputerService.getFeatureComputerFor( featureSpec ) )
				toClear.add( featureSpec );

		for ( final FeatureSpec< ?, ? > featureSpec : toClear )
			featureModel.clear( featureSpec );

		// Pass the feature map to the feature model.
		map.values().forEach( featureModel::declareFeature );

		featureModel.resumeListeners();
		logger.info( "Feature computation finished." );
	}

	/*
	 * DEMO
	 */

	public static void main( final String[] args ) throws IOException, SpimDataException
	{
		final String bdvFile = "../mastodon/samples/datasethdf5.xml";
		final Mamut mamut = Mamut.newProject( bdvFile );
		final TrackMate trackmate = mamut.createTrackMate();
		trackmate.getSettings().values.getDetectorSettings().put( "RADIUS", 7. );
		trackmate.getSettings().values.getDetectorSettings().put( "THRESHOLD", 200. );

		trackmate.run();
		if ( trackmate.isCanceled() )
			System.out.println( "Calculation was canceled. Reason: " + trackmate.getCancelReason() );
		else if ( !trackmate.isSuccessful() )
			System.out.println( "Calculation failed with error message:\n" + trackmate.getErrorMessage() );
		else
			System.out.println( "Calculation complete." );

		mamut.getWindowManager().createTrackScheme();

		mamut.computeFeatures( "Spot position", "Spot radius", "Dummy" );
		mamut.getWindowManager().createTable( false );
	}
}
