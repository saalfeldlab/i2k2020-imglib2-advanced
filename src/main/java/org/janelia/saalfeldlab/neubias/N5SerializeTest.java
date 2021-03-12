package org.janelia.saalfeldlab.neubias;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.concurrent.ExecutionException;

import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.neubias.util.N5Factory;
import org.janelia.saalfeldlab.neubias.util.Timer;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.NativeType;
import picocli.CommandLine;

public class N5SerializeTest {

	/**
	 * Start the tool.  We ignore the exit code returned by
	 * {@link CommandLine#execute(String...)} but this can be useful in other
	 * applications.
	 *
	 * @param args
	 * @throws ExecutionException
	 * @throws InterruptedException
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
	 * @throws ExecutionException
	 * @throws InterruptedException
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

		final ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
		try (ObjectOutputStream out = new ObjectOutputStream(byteOutputStream)) {
			out.writeObject(img);
		}
		final byte[] bytes = byteOutputStream.toByteArray();
		System.out.println(bytes.length);
	}
}
