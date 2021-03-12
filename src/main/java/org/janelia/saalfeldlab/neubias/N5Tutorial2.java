package org.janelia.saalfeldlab.neubias;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.neubias.util.N5Factory;
import org.janelia.saalfeldlab.neubias.util.Timer;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.NativeType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "i2k2020-tutorial-n5-2")
public class N5Tutorial2 implements Callable<Void> {

	@Option(
			names = {"-i", "--n5url"},
			required = true,
			description = "N5 URL, e.g. 'https://janelia-cosem.s3.amazonaws.com/jrc_hela-2/jrc_hela-2.n5'")
	private String n5Url = null;

	@Option(
			names = {"-d", "--n5dataset"},
			required = true,
			description = "N5 dataset, e.g. '/em/fibsem-uint16/s0'")
	private String n5Dataset = null;

	@Option(
			names = {"-o", "--n5outurl"},
			required = true,
			description = "N5 URL, e.g. '/home/saalfeld/tmp/jrc_hela-2'")
	private String n5OutUrl = null;

	@Option(
			names = {"-c", "--cropmin"},
			required = true,
			split = ",",
			description = "comma separated list of inclusive min coordinates, e.g. -c 2700,700,2440")
	private long[] cropMin = null;

	@Option(
			names = {"-l", "--cropmax"},
			required = true,
			split = ",",
			description = "comma separated list of inclusive max coordinates, e.g. -l 3499,1499,3239")
	private long[] cropMax = null;


	/**
	 * Start the tool.  We ignore the exit code returned by
	 * {@link CommandLine#execute(String...)} but this can be useful in other
	 * applications.
	 *
	 * @param args
	 */
	public static void main(final String... args) {

		new CommandLine(new N5Tutorial2()).execute(args);
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
	 * Copy a crop of a dataset from AWS S3 to local disk (N5, Zarr, HDF5).
	 *
	 * @param <T>
	 * @throws IOException
	 * @throws ExecutionException
	 * @throws InterruptedException
	 */
	private final <T extends NativeType<T>> void run() throws IOException, InterruptedException, ExecutionException {

		final Timer timer = new Timer();

		/* make an N5 reader, we start with a public container on AWS S3 */
		final N5Reader n5 = N5Factory.openReader(n5Url);

		/* open the dataset */
		final RandomAccessibleInterval<T> img = N5Utils.open(n5, n5Dataset);
		final IntervalView<T> crop = Views.interval(img, cropMin, cropMax);
		final DatasetAttributes attributes = n5.getDatasetAttributes(n5Dataset);

		/* working in parallel helps */
//		final ExecutorService exec = Executors.newCachedThreadPool();
		final ExecutorService exec = Executors.newFixedThreadPool(10);

		/* save this dataset into a filsystem N5 container */
		System.out.println("Copy to N5 filesystem...");
		timer.start();
		try (final N5Writer n5Out = N5Factory.openFSWriter(n5OutUrl + ".n5")) {
//			N5Utils.save(crop, n5Out, n5Dataset, attributes.getBlockSize(), attributes.getCompression());
			N5Utils.save(crop, n5Out, n5Dataset, attributes.getBlockSize(), attributes.getCompression(), exec);
			n5Out.setAttribute(n5Dataset, "offset", cropMin);
		}
		System.out.println("...done in " + timer.stop() + "ms.");

		/* save this dataset into a filsystem Zarr container */
		System.out.println("Copy to Zarr filesystem...");
		timer.start();
		try (final N5Writer zarrOut = N5Factory.openZarrWriter(n5OutUrl + ".zarr")) {
//			N5Utils.save(crop, zarrOut, n5Dataset, attributes.getBlockSize(), attributes.getCompression());
			N5Utils.save(crop, zarrOut, n5Dataset, attributes.getBlockSize(), attributes.getCompression(), exec);
			zarrOut.setAttribute(n5Dataset, "offset", cropMin);
		}
		System.out.println("...done in " + timer.stop() + "ms.");

		/* save this dataset into an HDF5 file */
		System.out.println("Copy to HDF5...");
		timer.start();
		try (final N5Writer hdf5Out = N5Factory.openHDF5Writer(n5OutUrl + ".hdf5", attributes.getBlockSize())) {
			N5Utils.save(crop, hdf5Out, n5Dataset, attributes.getBlockSize(), attributes.getCompression());
//			N5Utils.save(crop, hdf5Out, n5Dataset, attributes.getBlockSize(), attributes.getCompression(), exec);
			hdf5Out.setAttribute(n5Dataset, "offset", cropMin);
		}
		System.out.println("...done in " + timer.stop() + "ms.");

		exec.shutdown();
	}
}
