package org.janelia.saalfeldlab.i2k2020;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Callable;

import org.janelia.saalfeldlab.n5.DataType;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;

import bdv.util.BdvFunctions;
import bdv.util.volatiles.VolatileViews;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converters;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "i2k2020-tutorial")
public class N5Tutorial implements Callable<Void> {

	@Option(names = { "--n5url" }, required = true, description = "N5 URL, e.g. 'https://janelia-cosem.s3.amazonaws.com/jrc_hela-2/jrc_hela-2.n5'")
	private String n5Url = null;

	@Option(names = { "--n5Dataset" }, required = true, description = "N5 dataset, e.g. '/em/fibsem-uint16/s4'")
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

		final RandomAccessibleInterval<T> img = N5Utils.openVolatile(n5, n5Dataset);
		final RandomAccessibleInterval<UnsignedShortType> inverted = Converters.convert(
				img,
				(a, b) -> b.set(0xffff - a.getInteger()),
				new UnsignedShortType());

		BdvFunctions.show(VolatileViews.wrapAsVolatile(Caches.cache(inverted, 16, 16, 16)), "");
	}
}
