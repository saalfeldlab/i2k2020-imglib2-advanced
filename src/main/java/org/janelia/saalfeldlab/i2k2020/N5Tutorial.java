package org.janelia.saalfeldlab.i2k2020;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Callable;

import org.janelia.saalfeldlab.n5.DataType;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;

import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.util.volatiles.VolatileViews;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.converter.Converters;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.DoubleType;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "i2k2020-tutorial")
public class N5Tutorial implements Callable<Void> {

	@Option(names = {"-i", "--n5url"}, required = true, description = "N5 URL, e.g. 'https://janelia-cosem.s3.amazonaws.com/jrc_hela-2/jrc_hela-2.n5'")
	private String n5Url = null;

	@Option(names = {"-s", "--n5dataset"}, required = true, description = "N5 dataset, e.g. '/em/fibsem-uint16/s4'")
	private String n5Dataset = null;

	public static void main(final String... args) {

		new CommandLine(new N5Tutorial()).execute(args);
	}

	@Override
	public Void call() throws Exception {

		run();
		return null;
	}

	private final <T extends NativeType<T> & IntegerType<T>> void run() throws IOException {

		final N5Reader n5 = N5Factory.openN5AWSS3Reader(n5Url);
		final DataType dataType = n5.getAttribute(n5Dataset, "dataType", DataType.class);
		System.out.println(dataType);

		final Map<String, Class<?>> attributes = n5.listAttributes(n5Dataset);
		System.out.println(attributes);

		/* open the dataset */
		// final RandomAccessibleInterval<T> img = N5Utils.open(n5, n5Dataset);

		/* open the dataset, use volatile access */
		final RandomAccessibleInterval<T> img = N5Utils.openVolatile(n5, n5Dataset);

		/* show with BDV */
		// final BdvStackSource<T> bdv = BdvFunctions.show(img, n5Dataset);

		/* we want to change some BDV options later */
		final BdvOptions options = BdvOptions.options()
				.numRenderingThreads(3)
				.screenScales(new double[] {0.5});

		/* show with BDV, wrapped as volatile */
		final BdvStackSource<Volatile<T>> bdv = BdvFunctions.show(
				VolatileViews.wrapAsVolatile(img),
				n5Dataset,
				options);

		final RandomAccessibleInterval<UnsignedShortType> inverted = Converters.convert(
				img,
				(a, b) -> b.set(0xffff - a.getInteger()),
				new UnsignedShortType());

		/* show with BDV, adding to existing instance */
//		BdvFunctions.show(inverted, n5Dataset + " inverted", options.addTo(bdv));

		final RandomAccessibleInterval<UnsignedShortType> cached = Caches.cache(inverted, 16, 16, 16);

		BdvFunctions.show(
				VolatileViews.wrapAsVolatile(cached),
				n5Dataset + " inverted",
				options.addTo(bdv));

		final RandomAccessibleInterval<DoubleType> invertedDoubles = Converters.convert(
				inverted,
				(a, b) -> b.setReal(a.getRealDouble()),
				new DoubleType());
		/* gradients */
		final RandomAccessibleInterval<DoubleType> gradientX = Functions.centerGradientRAI(invertedDoubles, 0);

		BdvFunctions.show(
				VolatileViews.wrapAsVolatile(
						Caches.cache(gradientX, 16, 16, 16)),
				n5Dataset + " gradient x",
				options.addTo(bdv));

	}
}
