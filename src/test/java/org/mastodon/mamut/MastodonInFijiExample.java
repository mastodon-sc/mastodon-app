package org.mastodon.mamut;

import org.mastodon.mamut.launcher.MastodonLauncherCommand;

import net.imagej.ImageJ;

public class MastodonInFijiExample
{

	public static void main( final String[] args ) throws Exception
	{
		final ImageJ ij = new ImageJ();
		ij.launch( args );
		ij.command().run( MastodonLauncherCommand.class, false );
	}
}
