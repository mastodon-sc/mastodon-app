package org.mastodon.mamut;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.scijava.log.Logger;

import mpicbg.spim.data.SpimDataException;

public class DemoMamut
{

	public static void main( final String[] args ) throws IOException, SpimDataException
	{
//		final String newMastodonFile = "../mastodon/samples/test_scripting.mastodon";
//		final Mamut mamut = Mamut.open( newMastodonFile );

		final String bdvFile = "../mastodon/samples/datasethdf5.xml";
		final Mamut mamut = Mamut.newProject( bdvFile );
		final Logger logger = mamut.getLogger();

		/*
		 * Run default detection and linking algorithms.
		 */

		logger.info( "\n\n-------------------------------------" );
		logger.info( "\n  Basic detection and linking" );
		logger.info( "\n-------------------------------------\n" );

		mamut.detect( 6., 200. );
		mamut.link( 15., 0 );

		/*
		 * For a deeper configuration of the detection and linking algorithm,
		 * create a TrackMate and configure its settings.
		 */

		logger.info( "\n\n-------------------------------------" );
		logger.info( "\n  Running TrackMate" );
		logger.info( "\n-------------------------------------\n" );

		mamut.clear();

		final TrackMateProxy trackmate = mamut.createTrackMate();

		trackmate.infoDetectors();
//		trackmate.infoLinkers();

		trackmate.useDetector( "Advanced DoG detector" );
		trackmate.setDetectorSetting( "RADIUS", 8. );
		trackmate.setDetectorSetting( "THRESHOLD", 200. );
		trackmate.setDetectorSetting( "ADD_BEHAVIOR", "DONTADD" );
		trackmate.info();
		trackmate.run();

		/*
		 * Feature computation.
		 */

		logger.info( "\n\n-------------------------------------" );
		logger.info( "\n  Feature computation" );
		logger.info( "\n-------------------------------------\n" );

		mamut.infoFeatures();
		mamut.computeFeatures( "Spot position", "Spot radius", "Dummy" );

		/*
		 * Undo and redo.
		 */

//		logger.info( "\n\n-------------------------------------" );
//		logger.info( "\n  Undo & redo" );
//		logger.info( "\n-------------------------------------\n" );
//
//		logger.info( "Before clearing:\n" );
//		mamut.info();
//
//		mamut.clear();
//		mamut.getLogger().info( "After clearing:\n" );
//		mamut.info();
//
//		mamut.undo();
//		mamut.getLogger().info( "After undo:\n" );
//		mamut.info();

		/*
		 * Let's delete tracks shorter than 10 detections.
		 */

		logger.info( "\n\n-------------------------------------" );
		logger.info( "\n  Selecting objects" );
		logger.info( "\n-------------------------------------\n" );

		mamut.select( "vertexFeature( 'Track N spots' ) < 10" );

		// Ah yes we need to compute it first.
		mamut.computeFeatures( "Track N spots" );

		mamut.select( "vertexFeature( 'Track N spots' ) < 10" );
		mamut.deleteSelection();

		/*
		 * Tagging
		 */

		logger.info( "\n\n-------------------------------------" );
		logger.info( "\n  Tagging" );
		logger.info( "\n-------------------------------------\n" );

		mamut.infoTags();

		mamut.createTag( "Fruits", "Apple", "Banana", "Kiwi" );
		mamut.createTag( "Persons", "Tobias", "Jean-Yves" );

		mamut.setTagColor( "Fruits", "Apple", 200, 0, 0 );
		mamut.setTagColor( "Fruits", "Banana", 200, 200, 0 );
		mamut.setTagColor( "Fruits", "Kiwi", 0, 200, 0 );

		mamut.infoTags();
		mamut.computeFeatures( "Spot position", "Spot frame", "Spot N links" );

		mamut.select( "vertexFeature('Spot position' ,'X' ) > 100." );
		mamut.tagSelectionWith( "Fruits", "Kiwi" );

		mamut.select( "vertexFeature('Spot frame' ) == 25" );
		mamut.tagSelectionWith( "Fruits", "Banana" );

		mamut.select( "vertexFeature('Spot N links' ) == 1" );
		mamut.tagSelectionWith( "Fruits", "Apple" );

		mamut.resetSelection();

		// Create a TrackScheme with the coloring from the tag.
		final Map< String, Object > displaySettings = new HashMap< String, Object >();
		displaySettings.put( "TagSet", "Fruits" );
		mamut.getWindowManager().createTrackScheme( displaySettings );

//		/*
//		 * Saving.
//		 */
//
//		logger.info( "\n\n-------------------------------------" );
//		logger.info( "\n  Saving and loading" );
//		logger.info( "\n-------------------------------------\n" );
//
//		mamut.save();
//		final String newMastodonFile = "../mastodon/samples/test_scripting.mastodon";
//		mamut.saveAs( newMastodonFile );
//
//		/*
//		 * Reloading on another instance.
//		 */
//
//		mamut.logger.info( "After reloading:\n" );
//		Mamut.open( newMastodonFile ).info();

		/*
		 * Display all.
		 */

		logger.info( "\n\n---------------------------------------------------" );
		logger.info( "\n  Display all values for the first 10 objects" );
		logger.info( "\n---------------------------------------------------\n" );

		mamut.computeFeatures( "Spot radius", "Spot gaussian-filtered intensity", "Spot N links", "Track N spots" );
		mamut.echo( 10 );
	}

}
