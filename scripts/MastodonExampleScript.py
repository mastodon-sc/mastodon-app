#@ Context context

from org.mastodon.mamut import Mamut
import os

# If you want to open an existing Mastodon file, here is the command.
# newMastodonFile = "/Users/tinevez/Development/Mastodon/mastodon/samples/test_scripting.mastodon";
# Mamut.open( newMastodonFile, context )

# But in the following we will start from an image.
cwd = os.getcwd()
bdvFile = os.path.join( cwd, 'samples/datasethdf5.xml' )
if not os.path.exists( bdvFile ):
	print( 'The file %s cannot be found. Please enter the path of a valid BDV file.' % bdvFile )
else:
	
	mamut = Mamut.newProject( bdvFile, context )
	
	# By defaut this will show messages in Fiji log.
	logger = mamut.getLogger()
	
	#------------------------------------------------------------------
	# Run default detection and linking algorithms.
	#------------------------------------------------------------------
	
	logger.info( "\n\n-------------------------------------" )
	logger.info( "\n  Basic detection and linking" )
	logger.info( "\n-------------------------------------\n" )
	
	# Detect with the DoG detector, a radius of 6 and a threshold on quality of 200.
	mamut.detect( 6., 200. )
	# Link spots with the simple LAP tracker, with a max linking distance of 10.
	mamut.link( 10., 0 )
	
	
	#------------------------------------------------------------------
	# For a deeper configuration of the detection and linking algorithm,
	# create a TrackMate and configure its settings.
	#------------------------------------------------------------------
	
	logger.info( "\n\n-------------------------------------" )
	logger.info( "\n  Running TrackMate" )
	logger.info( "\n-------------------------------------\n" )
	
	# Reset tracking data.
	# mamut.clear()
	
	# The TrackMate object is a proxy that lets you run and configure 
	# the tracking process in detail. 
	trackmate = mamut.createTrackMate()
	
	# Print info on available detectors and linkers.
	trackmate.infoDetectors();
	#	trackmate.infoLinkers();
	
	# Configure tracking.
	trackmate.useDetector( "Advanced DoG detector" );
	trackmate.setDetectorSetting( "RADIUS", 8. );
	trackmate.setDetectorSetting( "THRESHOLD", 200. );
	trackmate.setDetectorSetting( "ADD_BEHAVIOR", "DONTADD" );
	# Show info on the config we have.
	trackmate.info();
	# Run the full tracking process.
	trackmate.run();
	
	
	#------------------------------------------------------------------
	# Feature computation.
	#------------------------------------------------------------------
	
	logger.info( "\n\n-------------------------------------" )
	logger.info( "\n  Feature computation" )
	logger.info( "\n-------------------------------------\n" )
	
	mamut.infoFeatures()
	mamut.computeFeatures( "Spot intensity", "Dummy" )
	# It will complain about being un able to find a computer for the feature called "Dummy"
	
	
	
	#------------------------------------------------------------------
	# Undo and redo.
	#------------------------------------------------------------------
	
	logger.info( "\n\n-------------------------------------" )
	logger.info( "\n  Undo & redo" )
	logger.info( "\n-------------------------------------\n" )
	
	logger.info( "Before:\n" )
	mamut.info()
	
	# Let's delete 500 spots.
	it = mamut.getModel().getGraph().vertices().iterator()
	for i in range( 500 ):
		if it.hasNext():
			spot = it.next()
			mamut.getModel().getGraph().remove ( spot )
	
	# We mark this point as an undo point. Calling undo() / redo()
	# navigates in the stack of these undo points. 
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
	# You should get an error here.
	
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
	
	# Show info on current tags. Should be empty.
	mamut.infoTags()
	
	# Let's create some. 
	# We create a 'Fruits' tag-set, with 'Apple', 'Banana', 'Kiwi' as tags.
	mamut.createTag( "Fruits", "Apple", "Banana", "Kiwi" )
	# The same with a 'Persons' tag-set.
	mamut.createTag( "Persons", "Tobias", "Jean-Yves" )
	
	# Tags have colors that can be specified as follow:
	mamut.setTagColor( "Fruits", "Apple", 200, 0, 0 )
	mamut.setTagColor( "Fruits", "Banana", 200, 200, 0 )
	mamut.setTagColor( "Fruits", "Kiwi", 0, 200, 0 )
	
	# Info on the new tags.
	mamut.infoTags()
	
	# Below, we use features that are computed on the fly for selection.
	# So we do not have to explicitely call for mamut.computeFeatures() 
	# to use them.
	
	# To tag objects, you need to place them in the selection first.
	# This is done with the select() command, as seen above.
	mamut.select( "vertexFeature('Spot position' ,'X' ) > 100." )
	# Then you simply have to call:
	mamut.tagSelectionWith( "Fruits", "Kiwi" )
	
	mamut.select( "vertexFeature('Spot frame' ) == 25" )
	mamut.tagSelectionWith( "Fruits", "Banana" )
	
	mamut.select( "vertexFeature('Spot N links' ) == 1" )
	mamut.tagSelectionWith( "Fruits", "Apple" )
	
	# Clear selection.
	mamut.resetSelection()
	
	
	#------------------------------------------------------------------
	# Saving.
	#------------------------------------------------------------------
	
	logger.info( "\n\n-------------------------------------" )
	logger.info( "\n  Saving and loading" )
	logger.info( "\n-------------------------------------\n" )
	
	# Save current project.
	# It will complain, because we did not define a project file yet.
	mamut.save()
	
	# Save to a specified file.
	newMastodonFile = os.path.join( cwd, 'samples/test_scripting.mastodon'
 )
	mamut.saveAs( newMastodonFile )
	
# From now on, save() will save to this file.
	
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
	
	# Some of the features listed below are computed on the fly and need not 
	# to be recomputed. A warning will be displayed about them.
	mamut.computeFeatures( "Spot radius", "Spot center intensity", "Spot N links", "Track N spots" )
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
	mamut.getWindowManager().createSelectionTable( )

	# Cool no?
	