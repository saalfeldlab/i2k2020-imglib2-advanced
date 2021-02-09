package org.janelia.saalfeldlab.neubias;

import java.io.IOException;

import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.neubias.util.Caches;
import org.janelia.saalfeldlab.neubias.util.N5Factory;

import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.util.volatiles.VolatileViews;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converters;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;
import picocli.CommandLine.Command;

@Command(name = "i2k2020-tutorial-n5-4")
public class N5Tutorial3 {

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
	 * Calculate the gradients of the dataset, cache them and show them in BDV.
	 *
	 * @param <T>
	 * @throws IOException
	 */
	private static final <T extends RealType<T> & NativeType<T>> void run() throws IOException {

		final String n5Url = "https://janelia-cosem.s3.amazonaws.com/jrc_hela-2/jrc_hela-2.n5";
		final String n5Dataset = "/em/fibsem-uint16/s4";

		/* make an N5 reader, we start with a public container on AWS S3 */
		final N5Reader n5 = N5Factory.openAWSS3Reader(n5Url);

		/* open the dataset, use volatile access */
		final RandomAccessibleInterval<T> img = N5Utils.openVolatile(n5, n5Dataset);

		/* show with BDV, wrapped as volatile */
		BdvStackSource<?> bdv = BdvFunctions.show(
				VolatileViews.wrapAsVolatile(img),
				n5Dataset);
		bdv.setDisplayRange(24000, 32000);

		/* convert to a signed type that can capture the gradients */
		final RandomAccessibleInterval<DoubleType> imgDoubles = Converters.convert(
				img,
				(a, b) -> b.setReal(a.getRealDouble()),
				new DoubleType());

		/* gradients X */
		final RandomAccessibleInterval<DoubleType> gradientX = Functions.centerGradientRAI(imgDoubles, 0);
		/* gradients Y */
		final RandomAccessibleInterval<DoubleType> gradientY = Functions.centerGradientRAI(imgDoubles, 1);
		/* gradients Z */
		final RandomAccessibleInterval<DoubleType> gradientZ = Functions.centerGradientRAI(imgDoubles, 2);


		bdv = BdvFunctions.show(
				VolatileViews.wrapAsVolatile(
						Caches.cache(gradientX, 16, 16, 16)),
				n5Dataset + " gradient x",
				BdvOptions.options().addTo(bdv));
		bdv.setColor(new ARGBType(0xffff0000));
		bdv.setDisplayRange(-2000, 2000);

		bdv = BdvFunctions.show(
				VolatileViews.wrapAsVolatile(
						Caches.cache(gradientY, 16, 16, 16)),
				n5Dataset + " gradient y",
				BdvOptions.options().addTo(bdv));
		bdv.setColor(new ARGBType(0xff00ff00));
		bdv.setDisplayRange(-2000, 2000);

		bdv = BdvFunctions.show(
				VolatileViews.wrapAsVolatile(
						Caches.cache(gradientZ, 16, 16, 16)),
				n5Dataset + " gradient z",
				BdvOptions.options().addTo(bdv));
		bdv.setColor(new ARGBType(0xff0000ff));
		bdv.setDisplayRange(-2000, 2000);
	}
}
