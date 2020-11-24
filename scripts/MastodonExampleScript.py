#@ Context context

from org.mastodon.mamut import Mamut

# If you want to open an existing Mastodon file, here is the command.
# newMastodonFile = "/Users/tinevez/Development/Mastodon/mastodon/samples/test_scripting.mastodon";
# Mamut.open( newMastodonFile, context )

# But in the following we will start from an image.
bdvFile = "/Users/tinevez/Development/Mastodon/mastodon/samples/datasethdf5.xml"
mamut = Mamut.newProject( bdvFile, context )
logger = mamut.getLogger()


#------------------------------------------------------------------
# Run default detection and linking algorithms.
#------------------------------------------------------------------

logger.info( "\n\n-------------------------------------" )
logger.info( "\n  Basic detection and linking" )
logger.info( "\n-------------------------------------\n" )

mamut.detect( 6., 200. )
mamut.link( 15., 0 )


#------------------------------------------------------------------
# For a deeper configuration of the detection and linking algorithm,
# create a TrackMate and configure its settings.
#------------------------------------------------------------------

logger.info( "\n\n-------------------------------------" )
logger.info( "\n  Running TrackMate" )
logger.info( "\n-------------------------------------\n" )

#mamut.clear()

trackmate = mamut.createTrackMate()
trackmate.infoDetectors();
#	trackmate.infoLinkers();

trackmate.useDetector( "Advanced DoG detector" );
trackmate.setDetectorSetting( "RADIUS", 8. );
trackmate.setDetectorSetting( "THRESHOLD", 200. );
trackmate.setDetectorSetting( "ADD_BEHAVIOR", "DONTADD" );
trackmate.info();
trackmate.run();


#------------------------------------------------------------------
# Feature computation.
#------------------------------------------------------------------

logger.info( "\n\n-------------------------------------" )
logger.info( "\n  Feature computation" )
logger.info( "\n-------------------------------------\n" )

mamut.infoFeatures()
mamut.computeFeatures( "Spot position", "Spot radius", "Dummy" )


#------------------------------------------------------------------
# Undo and redo.
#------------------------------------------------------------------

logger.info( "\n\n-------------------------------------" )
logger.info( "\n  Undo & redo" )
logger.info( "\n-------------------------------------\n" )

logger.info( "Before:\n" )
mamut.info()

it = mamut.getModel().getGraph().vertices().iterator()
for i in range( 500 ):
	if it.hasNext():
		spot = it.next()
		mamut.getModel().getGraph().remove ( spot )
mamut.getModel().setUndoPoint()

logger.info( "After deleting some spots:\n" )
mamut.info()

mamut.undo()
logger.info( "After undo:\n" )
mamut.info()


#------------------------------------------------------------------
# Let's delete tracks shorter than 10 detections.
#------------------------------------------------------------------

logger.info( "\n\n-------------------------------------" )
logger.info( "\n  Selecting objects" )
logger.info( "\n-------------------------------------\n" )

mamut.select( "vertexFeature( 'Track N spots' ) < 10" )

# Ah yes we need to compute it first.
mamut.computeFeatures( "Track N spots" )

mamut.select( "vertexFeature( 'Track N spots' ) < 10" )
mamut.deleteSelection()


#------------------------------------------------------------------
# Tagging
#------------------------------------------------------------------

logger.info( "\n\n-------------------------------------" )
logger.info( "\n  Tagging" )
logger.info( "\n-------------------------------------\n" )

mamut.infoTags()

mamut.createTag( "Fruits", "Apple", "Banana", "Kiwi" )
mamut.createTag( "Persons", "Tobias", "Jean-Yves" )

mamut.setTagColor( "Fruits", "Apple", 200, 0, 0 )
mamut.setTagColor( "Fruits", "Banana", 200, 200, 0 )
mamut.setTagColor( "Fruits", "Kiwi", 0, 200, 0 )

mamut.infoTags()
mamut.computeFeatures( "Spot position", "Spot frame", "Spot N links" )

mamut.select( "vertexFeature('Spot position' ,'X' ) > 100." )
mamut.tagSelectionWith( "Fruits", "Kiwi" )

mamut.select( "vertexFeature('Spot frame' ) == 25" )
mamut.tagSelectionWith( "Fruits", "Banana" )

mamut.select( "vertexFeature('Spot N links' ) == 1" )
mamut.tagSelectionWith( "Fruits", "Apple" );

mamut.resetSelection()


#------------------------------------------------------------------
# Saving.
#------------------------------------------------------------------

logger.info( "\n\n-------------------------------------" )
logger.info( "\n  Saving and loading" )
logger.info( "\n-------------------------------------\n" )

mamut.save()
newMastodonFile = "/Users/tinevez/Development/Mastodon/mastodon/samples/test_scripting.mastodon"
mamut.saveAs( newMastodonFile )


#------------------------------------------------------------------
# Reloading on another instance.
#------------------------------------------------------------------

mamut.logger.info( "After reloading:\n" )
Mamut.open( newMastodonFile, context ).info()


#------------------------------------------------------------------
# Display data as text. 
#------------------------------------------------------------------

logger.info( "\n\n---------------------------------------------------" )
logger.info( "\n  Display all values for the first 10 objects" )
logger.info( "\n---------------------------------------------------\n" )

mamut.computeFeatures( "Spot radius", "Spot gaussian-filtered intensity", "Spot N links", "Track N spots", "Spot radius" )
mamut.echo( 10 )


#------------------------------------------------------------------
# Create some views.
#------------------------------------------------------------------

# Create a TrackScheme with the coloring from the tag.
displaySettings = {}
displaySettings[ "TagSet" ] = "Fruits"
mamut.getWindowManager().createTrackScheme( displaySettings );

# A BDV.
mamut.getWindowManager().createBigDataViewer()

# A selection table with all the long tracks.
mamut.select( "vertexFeature('Track N spots' ) > 20" )
mamut.getWindowManager().createTable( True )
