package org.janelia.saalfeldlab.neubias;

import java.io.IOException;

import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.neubias.util.N5Factory;
import org.janelia.saalfeldlab.neubias.util.Timer;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.NativeType;

public class N5Tutorial4 {

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
	 * Copy a complete dataset from AWS S3 to local disk (N5, Zarr, HDF5).
	 *
	 * @param <T>
	 * @throws IOException
	 */
	private static final <T extends NativeType<T>> void run() throws IOException {

		final String n5Url = "https://janelia-cosem.s3.amazonaws.com/jrc_hela-2/jrc_hela-2.n5";
		final String n5Dataset = "/em/fibsem-uint16/s4";
		final String n5OutUrl = "/home/saalfeld/tmp/jrc_hela-2";


		final Timer timer = new Timer();

		/* make an N5 reader, we start with a public container on AWS S3 */
		final N5Reader n5 = N5Factory.openReader(n5Url);

		/* open the dataset */
		final RandomAccessibleInterval<T> img = N5Utils.open(n5, n5Dataset);

		final DatasetAttributes attributes = n5.getDatasetAttributes(n5Dataset);

		/* save this dataset into a filsystem N5 container */
		System.out.println("Copy to N5 filesystem...");
		timer.start();
		try (final N5Writer n5Out = N5Factory.openFSWriter(n5OutUrl + ".n5")) {
			N5Utils.save(img, n5Out, n5Dataset, attributes.getBlockSize(), attributes.getCompression());
		}
		System.out.println("...done in " + timer.stop() + "ms.");

		/* save this dataset into a filsystem Zarr container */
		System.out.println("Copy to Zarr filesystem...");
		timer.start();
		try (final N5Writer zarrOut = N5Factory.openZarrWriter(n5OutUrl + ".zarr")) {
			N5Utils.save(img, zarrOut, n5Dataset, attributes.getBlockSize(), attributes.getCompression());
		}
		System.out.println("...done in " + timer.stop() + "ms.");

		/* save this dataset into an HDF5 file */
		System.out.println("Copy to HDF5...");
		timer.start();
		try (final N5Writer hdf5Out = N5Factory.openHDF5Writer(n5OutUrl + ".hdf5", attributes.getBlockSize())) {
			N5Utils.save(img, hdf5Out, n5Dataset, attributes.getBlockSize(), attributes.getCompression());
		}
		System.out.println("...done in " + timer.stop() + "ms.");
	}
}
