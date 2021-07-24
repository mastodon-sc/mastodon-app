/*-
 * #%L
 * Mastodon
 * %%
 * Copyright (C) 2014 - 2021 Tobias Pietzsch, Jean-Yves Tinevez
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

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.mastodon.collection.RefSet;
import org.mastodon.feature.Feature;
import org.mastodon.feature.FeatureModel;
import org.mastodon.feature.FeatureSpec;
import org.mastodon.feature.FeatureSpecsService;
import org.mastodon.graph.GraphIdBimap;
import org.mastodon.graph.algorithm.RootFinder;
import org.mastodon.mamut.feature.MamutFeatureComputer;
import org.mastodon.mamut.feature.MamutFeatureComputerService;
import org.mastodon.mamut.model.Link;
import org.mastodon.mamut.model.Model;
import org.mastodon.mamut.model.ModelGraph;
import org.mastodon.mamut.model.ModelUtils;
import org.mastodon.mamut.model.Spot;
import org.mastodon.mamut.project.MamutProject;
import org.mastodon.mamut.project.MamutProjectIO;
import org.mastodon.mamut.selectioncreator.SelectionParser;
import org.mastodon.model.SelectionModel;
import org.mastodon.model.tag.ObjTagMap;
import org.mastodon.model.tag.TagSetModel;
import org.mastodon.model.tag.TagSetStructure;
import org.mastodon.model.tag.TagSetStructure.Tag;
import org.mastodon.model.tag.TagSetStructure.TagSet;
import org.mastodon.tracking.detection.DetectionUtil;
import org.mastodon.tracking.detection.DetectorKeys;
import org.mastodon.tracking.linking.LinkerKeys;
import org.mastodon.tracking.linking.LinkingUtils;
import org.mastodon.tracking.mamut.detection.DoGDetectorMamut;
import org.mastodon.tracking.mamut.linking.SimpleSparseLAPLinkerMamut;
import org.mastodon.tracking.mamut.trackmate.Settings;
import org.mastodon.tracking.mamut.trackmate.TrackMate;
import org.mastodon.views.bdv.SharedBigDataViewerData;
import org.scijava.Context;
import org.scijava.command.CommandInfo;
import org.scijava.command.CommandService;
import org.scijava.log.AbstractLogService;
import org.scijava.log.LogLevel;
import org.scijava.log.LogMessage;
import org.scijava.log.Logger;
import org.scijava.module.ModuleItem;

import mpicbg.spim.data.SpimDataException;

public class Mamut
{

	private final WindowManager wm;

	private final MamutFeatureComputerService featureComputerService;

	private Logger logger = new AbstractLogService()
	{
		@Override
		protected void messageLogged( final LogMessage message )
		{
			final int level = message.level();
			if ( level <= LogLevel.WARN )
				System.err.print( message.text() );
			else
				System.out.print( message.text() );
		}
	};

	private final int ID;

	private Mamut( final WindowManager wm )
	{
		this.wm = wm;
		this.featureComputerService = wm.getContext().getService( MamutFeatureComputerService.class );
		featureComputerService.setModel( wm.getAppModel().getModel() );
		featureComputerService.setSharedBdvData( wm.getAppModel().getSharedBdvData() );
		this.ID = IDGENERATOR.getAndIncrement();
	}

	public static final Mamut open( final String mamutProject ) throws IOException, SpimDataException
	{
		return open( mamutProject, new Context() );
	}

	public static final Mamut open( final String mamutProject, final Context context ) throws IOException, SpimDataException
	{
		System.setProperty( "apple.laf.useScreenMenuBar", "true" );
		final MamutProject project = new MamutProjectIO().load( mamutProject );
		final WindowManager wm = new WindowManager( context );
		wm.getProjectManager().open( project );
		return new Mamut( wm );
	}

	public static final Mamut newProject( final String bdvFile, final Context context ) throws IOException, SpimDataException
	{
		System.setProperty( "apple.laf.useScreenMenuBar", "true" );
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
	 * Setters.
	 */

	public void setLogger( final Logger logger )
	{
		this.logger = logger;
	}

	/*
	 * Getters.
	 */

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

	public Logger getLogger()
	{
		return logger;
	}

	/*
	 * Output.
	 */

	public void save()
	{
		final File projectFile = wm.getProjectManager().getProject().getProjectRoot();
		if ( projectFile == null )
		{
			logger.warn( "Mastodon file not set. Please use #saveAs() first.\n" );
			return;
		}
		saveAs( projectFile.getAbsolutePath() );
	}

	public void saveAs( final String mastodonFile )
	{
		logger.info( "Saving to " + mastodonFile + '\n' );
		try
		{
			wm.getProjectManager().saveProject( new File( mastodonFile ) );
		}
		catch ( final IOException e )
		{
			logger.error( "Problem writing project to file " + mastodonFile + ":\n" + e.getMessage() );
		}
	}

	/*
	 * Selection methods.
	 */

	public void select( final String expression )
	{
		final Model model = getModel();
		final ModelGraph graph = model.getGraph();
		final GraphIdBimap< Spot, Link > graphIdBimap = model.getGraphIdBimap();
		final TagSetModel< Spot, Link > tagSetModel = model.getTagSetModel();
		final FeatureModel featureModel = model.getFeatureModel();
		final SelectionModel< Spot, Link > selectionModel = getSelectionModel();
		try
		{
			final SelectionParser< Spot, Link > selectionParser = new SelectionParser<>( graph, graphIdBimap, tagSetModel, featureModel, selectionModel );
			final boolean ok = selectionParser.parse( expression );
			if ( ok )
				logger.info( "Evaluation successful. Selection has now " + selectionModel.getSelectedVertices().size()
						+ " spots and " + selectionModel.getSelectedEdges().size() + " edges.\n" );
			else
				logger.error( "Evaluation failed:\n" + selectionParser.getErrorMessage() + '\n' );
		}
		catch ( final IllegalArgumentException e )
		{
			logger.error( "Unable to parse the expression: " + expression );
		}
	}

	public void resetSelection()
	{
		getSelectionModel().clearSelection();
	}

	public void deleteSelection()
	{
		final SelectionModel< Spot, Link > selection = getSelectionModel();
		final ReentrantReadWriteLock lock = getModel().getGraph().getLock();
		if ( selection.isEmpty() )
			return;

		lock.writeLock().lock();
		final ModelGraph graph = getModel().getGraph();
		try
		{
			final RefSet< Link > edges = selection.getSelectedEdges();
			final RefSet< Spot > vertices = selection.getSelectedVertices();

			int nLinks = 0;
			int nSpots = 0;

			selection.pauseListeners();

			for ( final Link e : edges )
			{
				graph.remove( e );
				nLinks++;
			}

			for ( final Spot v : vertices )
			{
				nLinks += v.edges().size();
				graph.remove( v );
				nSpots++;
			}

			getModel().setUndoPoint();
			graph.notifyGraphChanged();
			selection.resumeListeners();

			logger.info( "Removed " + nSpots + " spots and " + nLinks + " links.\n" );
		}
		finally
		{
			lock.writeLock().unlock();
		}
	}

	/*
	 * Info methods.
	 */

	public void echo()
	{
		logger.info( ModelUtils.dump( getModel() ) );
	}

	public void echo( final int nLines )
	{
		logger.info( ModelUtils.dump( getModel(), nLines ) );
	}

	public void info()
	{
		final StringBuilder str = new StringBuilder();
		str.append( "Data model #" + this.ID + '\n' );
		str.append( " - mastodon project file: " );
		final File mastodonFile = wm.getProjectManager().getProject().getProjectRoot();
		if ( mastodonFile == null )
			str.append( "Not defined.\n" );
		else
			str.append( mastodonFile.getAbsolutePath() + '\n' );
		str.append( " - dataset: " + wm.getProjectManager().getProject().getDatasetXmlFile() + '\n' );
		str.append( String.format( " - objects: %d spots, %d links and %d tracks.\n",
				getModel().getGraph().vertices().size(),
				getModel().getGraph().edges().size(),
				RootFinder.getRoots( getModel().getGraph() ).size() ) );
		str.append( " - units: " + getModel().getSpaceUnits() + " and " + getModel().getTimeUnits() + '\n' );

		logger.info( str.toString() );
	}

	public void infoFeatures()
	{
		final List< CommandInfo > commandInfos = wm.getContext().getService( CommandService.class ).getCommandsOfType( MamutFeatureComputer.class );
		final FeatureSpecsService featureSpecs = wm.getContext().getService( FeatureSpecsService.class );

		final List< String > keys = new ArrayList<>();
		final List< String > infos = new ArrayList<>();
		for ( final CommandInfo commandInfo : commandInfos )
		{
			FeatureSpec< ?, ? > featureSpec = null;
			for ( final ModuleItem< ? > item : commandInfo.outputs() )
			{
				if ( !Feature.class.isAssignableFrom( item.getType() ) )
				{
					logger.warn( "Ignoring FeatureComputer " + commandInfo.getClassName()
							+ " because its output " + item + " is not a Feature.\n" );
					continue;
				}

				if ( featureSpec != null )
				{
					logger.warn( "Ignoring FeatureComputer " + commandInfo.getClassName()
							+ " because it defines more than one output.\n" );
					continue;
				}

				@SuppressWarnings( "unchecked" )
				final Class< ? extends Feature< ? > > type = ( Class< ? extends Feature< ? > > ) item.getType();
				featureSpec = featureSpecs.getSpec( type );
			}

			if ( featureSpec == null )
			{
				logger.warn( "Ignoring FeatureComputer " + commandInfo.getClassName()
						+ " because it does not define an output.\n" );
				continue;
			}

			keys.add( "'" + featureSpec.getKey() + "'" );
			infos.add( featureSpec.getInfo() );
		}

		final StringBuilder str = new StringBuilder();
		str.append( "Features that can be computed:" );

		final int width = keys.stream().map( String::length ).reduce( Math::max ).orElse( 5 ).intValue();
		for ( int i = 0; i < keys.size(); i++ )
			str.append( String.format( "\n - %-" + width + "s - %s", keys.get( i ), infos.get( i ) ) );

		logger.info( str.toString() + '\n' );
	}

	public void infoTags()
	{
		final TagSetModel< Spot, Link > tagModel = getModel().getTagSetModel();
		final List< TagSet > tagSets = tagModel.getTagSetStructure().getTagSets();

		if ( tagSets.isEmpty() )
		{
			logger.info( "No tags are currently defined.\n" );
			return;
		}

		final StringBuilder str = new StringBuilder();
		str.append( String.format( "%-49s R    G    B\n", "Tags currently defined:" ) );

		for ( final TagSet tagSet : tagSets )
		{
			str.append( String.format( " - %s:\n", tagSet.getName() ) );
			final List< Tag > tags = tagSet.getTags();

			if ( tags.isEmpty() )
			{
				str.append( "    - empty\n" );
				continue;
			}

			for ( final Tag tag : tags )
			{
				final Color color = new Color( tag.color(), true );
				str.append( String.format( "    - %-40s [ %3d, %3d, %3d ]\n",
						tag.label(),
						color.getRed(), color.getGreen(), color.getBlue() ) );
			}
		}

		logger.info( str.toString() + '\n' );
	}

	/*
	 * Undo =, redo and co.
	 */

	public void undo()
	{
		getModel().undo();
	}

	public void redo()
	{
		getModel().redo();
	}

	public void clear()
	{
		logger.info( "Clearing model.\n" );
		final ModelGraph graph = getModel().getGraph();
		for ( final Spot spot : graph.vertices() )
			graph.remove( spot );

		getModel().setUndoPoint();
	}

	/*
	 * TrackMate methods.
	 */

	public TrackMateProxy createTrackMate()
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
		return new TrackMateProxy( trackmate, logger );
	}

	public void detect( final double radius, final double threshold )
	{
		final TrackMate trackmate = createTrackMate().trackmate;
		trackmate.getSettings().values.getDetectorSettings().put( DetectorKeys.KEY_RADIUS, Double.valueOf( radius ) );
		trackmate.getSettings().values.getDetectorSettings().put( DetectorKeys.KEY_THRESHOLD, Double.valueOf( threshold ) );
		trackmate.execDetection();
	}

	public void link( final double maxLinkingDistance, final int maxFrameGap )
	{
		final TrackMate trackmate = createTrackMate().trackmate;
		trackmate.getSettings().values.getLinkerSettings().put( LinkerKeys.KEY_LINKING_MAX_DISTANCE, Double.valueOf( maxLinkingDistance ) );
		trackmate.getSettings().values.getLinkerSettings().put( LinkerKeys.KEY_GAP_CLOSING_MAX_DISTANCE, Double.valueOf( maxLinkingDistance ) );
		trackmate.getSettings().values.getLinkerSettings().put( LinkerKeys.KEY_GAP_CLOSING_MAX_FRAME_GAP, Integer.valueOf( maxFrameGap ) );
		trackmate.execParticleLinking();
	}

	/*
	 * Feature methods.
	 */

	public void computeFeatures( final String... featureKeys )
	{
		computeFeatures( false, featureKeys );
	}

	public void computeFeatures( final boolean forceComputeAll, final String... featureKeys )
	{
		logger.info( "Feature computation started.\n" );
		final Collection< FeatureSpec< ?, ? > > featureSpecs = new ArrayList<>();
		for ( final String key : featureKeys )
		{
			final FeatureSpec< ?, ? > spec = wm.getFeatureSpecsService().getSpec( key );
			if ( null == spec )
			{
				logger.warn( "Could not find a feature corresponding to the key " + key + ". Skipping.\n" );
				continue;
			}

			if ( spec.getTargetClass().equals( Spot.class ) || spec.getTargetClass().equals( Link.class ) )
				featureSpecs.add( spec );
			else
				logger.warn( "The feature " + key + " is defined for " + spec.getTargetClass() + " objects, not for spots or links. Skipping.\n" );
		}

		final Map< FeatureSpec< ?, ? >, Feature< ? > > map = featureComputerService.compute( forceComputeAll, featureSpecs );
		if ( featureComputerService.isCanceled() )
		{
			logger.warn( "Feature computation canceled. Reason: " + featureComputerService.getCancelReason() + '\n' );
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
		logger.info( "Feature computation finished.\n" );
	}

	/*
	 * Tags methods.
	 */

	public void createTag( final String tagSetName, final String... labels )
	{
		final TagSetStructure tss = new TagSetStructure();
		final TagSetModel< Spot, Link > tagModel = getModel().getTagSetModel();
		tss.set( tagModel.getTagSetStructure() );

		final TagSet tagSet = tss.createTagSet( tagSetName );
		for ( final String tag : labels )
			tagSet.createTag( tag, colorGenerator.next() );

		tagModel.setTagSetStructure( tss );
		getModel().setUndoPoint();
	}

	public void setTagColor( final String tagSetName, final String label, final int R, final int G, final int B )
	{
		final TagSetStructure tss = new TagSetStructure();
		final TagSetModel< Spot, Link > tagModel = getModel().getTagSetModel();
		tss.set( tagModel.getTagSetStructure() );

		final Optional< TagSet > optionalTagSet = tss.getTagSets().stream().filter( ts -> ts.getName().equals( tagSetName ) ).findFirst();
		if ( !optionalTagSet.isPresent() )
		{
			logger.error( "Could not find a tag-set with the name: '" + tagSetName + "'\n" );
			return;
		}
		final TagSet tagSet = optionalTagSet.get();

		final Optional< Tag > optionalTag = tagSet.getTags().stream().filter( t -> t.label().equals( label ) ).findFirst();
		if ( !optionalTag.isPresent() )
		{
			logger.error( "Could not find a tag with label: '" + tagSetName + "' in the tag-set '" + tagSetName + "'\n" );
			return;
		}
		final Tag tag = optionalTag.get();
		tag.setColor( new Color( R, G, B ).getRGB() );

		tagModel.setTagSetStructure( tss );
		getModel().setUndoPoint();
	}

	public void tagSelectionWith( final String tagSetName, final String label )
	{
		final TagSetModel< Spot, Link > tagModel = getModel().getTagSetModel();
		tagModel.getTagSetStructure().getTagSets();
		final Optional< TagSet > optionalTagSet = tagModel.getTagSetStructure().getTagSets().stream().filter( ts -> ts.getName().equals( tagSetName ) ).findFirst();
		if ( !optionalTagSet.isPresent() )
		{
			logger.error( "Could not find a tag-set with the name: '" + tagSetName + "'\n" );
			return;
		}
		final TagSet tagSet = optionalTagSet.get();

		final Optional< Tag > optionalTag = tagSet.getTags().stream().filter( t -> t.label().equals( label ) ).findFirst();
		if ( !optionalTag.isPresent() )
		{
			logger.error( "Could not find a tag with label: '" + tagSetName + "' in the tag-set '" + tagSetName + "'\n" );
			return;
		}
		final Tag tag = optionalTag.get();

		final ObjTagMap< Spot, Tag > vertexTags = tagModel.getVertexTags().tags( tagSet );
		final ObjTagMap< Link, Tag > edgeTags = tagModel.getEdgeTags().tags( tagSet );

		final SelectionModel< Spot, Link > selection = getSelectionModel();
		selection.getSelectedVertices().forEach( v -> vertexTags.set( v, tag ) );
		selection.getSelectedEdges().forEach( e -> edgeTags.set( e, tag ) );
	}

	/*
	 * Utility classes.
	 */

	private static final AtomicInteger IDGENERATOR = new AtomicInteger();

	private static final Iterator< Integer > colorGenerator = new Iterator< Integer >()
	{
		private final Random ran = new Random();

		@Override
		public boolean hasNext()
		{
			return true;
		}

		@Override
		public Integer next()
		{
			return ran.nextInt() | 0xFF000000;
		}
	};
}
