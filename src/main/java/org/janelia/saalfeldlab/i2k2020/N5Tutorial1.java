package org.janelia.saalfeldlab.i2k2020;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.janelia.saalfeldlab.i2k2020.util.Timer;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.n5.universe.N5Factory;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.NativeType;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "i2k2020-tutorial-n5-1")
public class N5Tutorial1 implements Callable<Void> {

	@Option(
			names = {"-i", "--n5url"},
			required = true,
			description = "N5 URL, e.g. 'https://janelia-cosem.s3.amazonaws.com/jrc_hela-2/jrc_hela-2.n5'")
	private String n5Url = null;

	@Option(
			names = {"-d", "--n5dataset"},
			required = true,
			description = "N5 dataset, e.g. '/em/fibsem-uint16/s4'")
	private String n5Dataset = null;

	@Option(
			names = {"-o", "--n5outurl"},
			required = true,
			description = "N5 URL, e.g. '/home/saalfeld/tmp/jrc_hela-2'")
	private String n5OutUrl = null;

	/**
	 * Start the tool.  We ignore the exit code returned by
	 * {@link CommandLine#execute(String...)} but this can be useful in other
	 * applications.
	 *
	 * @param args
	 */
	public static void main(final String... args) {

		new CommandLine(new N5Tutorial1()).execute(args);
	}

	/**
	 * The real implementation.  We use {@link Callable Callable<Void>} instead
	 * of {@link Runnable} because {@link Runnable#run()} cannot throw
	 * {@link Exception Exceptions}.
	 *
	 * Since we would like to use some type parameters, we have to delegate to
	 * a method that was not declared in an interface without such parameters.
	 *
	 * @throws Exception
	 */
	@Override
	public Void call() throws Exception {

		run();
		return null;
	}

	/**
	 * Copy a complete dataset from AWS S3 to local disk (N5, Zarr, HDF5).
	 *
	 * @param <T>
	 * @throws IOException
	 * @throws ExecutionException
	 * @throws InterruptedException
	 */
	private final <T extends NativeType<T>> void run() throws IOException, InterruptedException, ExecutionException {

		final Timer timer = new Timer();

		/* make an N5 reader */
		final N5Reader n5 = new N5Factory().openReader(n5Url);

		/* open the dataset */
		final RandomAccessibleInterval<T> img = N5Utils.open(n5, n5Dataset);

		final DatasetAttributes attributes = n5.getDatasetAttributes(n5Dataset);

		/* working in parallel helps */
		final ExecutorService exec = Executors.newFixedThreadPool(10);

		/* create an N5 factory with reasonable defaults */
		final N5Factory n5Factory = new N5Factory()
				.hdf5DefaultBlockSize(attributes.getBlockSize())
				.hdf5OverrideBlockSize(true);

		/* save this dataset into a filsystem Zarr container */
		System.out.println("Copy to Zarr filesystem...");
		timer.start();
		try (final N5Writer zarrOut = n5Factory.openZarrWriter(n5OutUrl + ".zarr")) {
//			N5Utils.save(img, zarrOut, n5Dataset, attributes.getBlockSize(), attributes.getCompression());
			N5Utils.save(img, zarrOut, n5Dataset, attributes.getBlockSize(), attributes.getCompression(), exec);
		}
		System.out.println("...done in " + timer.stop() + "ms.");

		/* save this dataset into a filsystem N5 container */
		System.out.println("Copy to N5 filesystem...");
		timer.start();
		try (final N5Writer n5Out = n5Factory.openFSWriter(n5OutUrl + ".n5")) {
//			N5Utils.save(img, n5Out, n5Dataset, attributes.getBlockSize(), attributes.getCompression());
			N5Utils.save(img, n5Out, n5Dataset, attributes.getBlockSize(), attributes.getCompression(), exec);
		}
		System.out.println("...done in " + timer.stop() + "ms.");

		/* save this dataset into an HDF5 file */
		System.out.println("Copy to HDF5...");
		timer.start();
		try (final N5Writer hdf5Out = n5Factory.openHDF5Writer(n5OutUrl + ".hdf5")) {
//			N5Utils.save(img, hdf5Out, n5Dataset, attributes.getBlockSize(), attributes.getCompression());
			N5Utils.save(img, hdf5Out, n5Dataset, attributes.getBlockSize(), attributes.getCompression(), exec);
		}
		System.out.println("...done in " + timer.stop() + "ms.");

		exec.shutdown();
	}
}
