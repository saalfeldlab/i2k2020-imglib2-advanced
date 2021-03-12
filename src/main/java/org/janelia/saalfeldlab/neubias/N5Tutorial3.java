package org.janelia.saalfeldlab.neubias;

import java.io.IOException;
import java.util.concurrent.Callable;

import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.neubias.util.N5Factory;

import bdv.util.BdvFunctions;
import bdv.util.volatiles.SharedQueue;
import bdv.util.volatiles.VolatileViews;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.type.NativeType;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "i2k2020-tutorial-n5-3")
public class N5Tutorial3 implements Callable<Void> {

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

	/**
	 * Start the tool.  We ignore the exit code returned by
	 * {@link CommandLine#execute(String...)} but this can be useful in other
	 * applications.
	 *
	 * @param args
	 */
	public static void main(final String... args) {

		new CommandLine(new N5Tutorial3()).execute(args);
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
	 * Open and show an N5 dataset with {@link Volatile} cells.
	 *
	 * @param <T>
	 * @throws IOException
	 */
	private final <T extends NativeType<T>> void run() throws IOException {

		/* make an N5 reader, we start with a public container on AWS S3 */
		final N5Reader n5 = N5Factory.openReader(n5Url);

		/* open dataset */
//		final RandomAccessibleInterval<T> img = N5Utils.open(n5, n5Dataset);
		final RandomAccessibleInterval<T> img = N5Utils.openVolatile(n5, n5Dataset);

		/* show with BDV */
//		BdvFunctions.show(img, n5Dataset);
		final SharedQueue queue = new SharedQueue(Math.max(1, Runtime.getRuntime().availableProcessors() / 2));
		BdvFunctions.show(VolatileViews.wrapAsVolatile(img, queue), n5Dataset);
	}
}
