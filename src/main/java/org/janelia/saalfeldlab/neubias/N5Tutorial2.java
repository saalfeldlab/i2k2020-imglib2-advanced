package org.janelia.saalfeldlab.neubias;

import java.io.IOException;

import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.neubias.util.N5Factory;

import bdv.util.BdvFunctions;
import bdv.util.volatiles.SharedQueue;
import bdv.util.volatiles.VolatileViews;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.type.NativeType;

public class N5Tutorial2 {

	/**
	 * Start the tool.  Since we would like to use some type parameters, we
	 * have to delegate to a method that was not declared in an interface
	 * without such parameters.
	 *
	 * @param args
	 * @throws IOException
	 */
	public static void main(final String... args) throws IOException {

		run();
	}

	/**
	 * Open and show an N5 dataset with {@link Volatile} cells.
	 *
	 * @param <T>
	 * @throws IOException
	 */
	private static final <T extends NativeType<T>> void run() throws IOException {

		final String n5Url = "https://janelia-cosem.s3.amazonaws.com/jrc_hela-2/jrc_hela-2.n5";
		final String n5Dataset = "/em/fibsem-uint16/s4";

		/* make an N5 reader, we start with a public container on AWS S3 */
		final N5Reader n5 = N5Factory.openReader(n5Url);

		/* open dataset */
		final RandomAccessibleInterval<T> img = N5Utils.openVolatile(n5, n5Dataset);

		/* show with BDV */
		final SharedQueue queue = new SharedQueue(Math.max(1, Runtime.getRuntime().availableProcessors() / 2));
		BdvFunctions.show(VolatileViews.wrapAsVolatile(img, queue), n5Dataset).setDisplayRange(24000, 32000);
	}
}