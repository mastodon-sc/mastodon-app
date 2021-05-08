package org.mastodon.mamut;

import java.io.IOException;
import java.util.Locale;

import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import org.mastodon.mamut.project.MamutProject;
import org.mastodon.mamut.project.MamutProjectIO;
import org.scijava.Context;

import mpicbg.spim.data.SpimDataException;

public class LaunchMastodonExample
{

	public static void main( final String[] args ) throws IOException, SpimDataException, ClassNotFoundException, InstantiationException, IllegalAccessException, UnsupportedLookAndFeelException
	{
		UIManager.setLookAndFeel( UIManager.getSystemLookAndFeelClassName() );
		Locale.setDefault( Locale.ROOT );

//		final String mastodonFile = "D:\\Projects\\JMKim\\Data\\MastodonCrash\\210108_s1_v3\\xml\\composite.mastodon";
//		final String mastodonFile = "../matlab-mastodon-importer/demo/datasethdf5.mastodon";

//		final String mastodonFile = "../mastodon/samples/drosophila_crop.mastodon";
		final String mastodonFile = "D:/Projects/JYTinevez/MaMuT/Mastodon-dataset/MaMuT_Parhyale_demo-mamut.mastodon";
		final MamutProject project = new MamutProjectIO().load( mastodonFile );

//		final String bdvFile = "../mastodon/samples/datasethdf5.xml";
//		final MamutProject project = new MamutProject( null, new File( bdvFile ) );

		// final String bdvFile =
		// "D:\\Projects\\JMKim\\Data\\MastodonCrash\\210108_s1_v3\\xml\\composite.xml";
//		final String bdvFile = "../mastodon-pasteur/src/test/resources/org/mastodon/mamut/io/csv/TestMedianCSVImport.xml";

		final WindowManager windowManager = new WindowManager( new Context() );
		windowManager.getProjectManager().open( project );
		new MainWindow( windowManager ).setVisible( true );
	}
}
