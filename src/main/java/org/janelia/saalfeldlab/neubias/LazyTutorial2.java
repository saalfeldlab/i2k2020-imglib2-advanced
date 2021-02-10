package org.janelia.saalfeldlab.neubias;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.neubias.ops.GradientCenter;
import org.janelia.saalfeldlab.neubias.ops.Max;
import org.janelia.saalfeldlab.neubias.ops.Multiply;
import org.janelia.saalfeldlab.neubias.ops.SimpleGaussRA;
import org.janelia.saalfeldlab.neubias.ops.TubenessCenter;
import org.janelia.saalfeldlab.neubias.util.Lazy;
import org.janelia.saalfeldlab.neubias.util.N5Factory;

import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.util.volatiles.VolatileViews;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.converter.Converters;
import net.imglib2.img.basictypeaccess.AccessFlags;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.view.ExtendedRandomAccessibleInterval;
import net.imglib2.view.Views;

public class LazyTutorial2 {

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

	public static <T extends NativeType<T> & RealType<T>> void run() throws IOException {

		final String n5Url = "https://janelia-cosem.s3.amazonaws.com/jrc_hela-2/jrc_hela-2.n5";
		final String n5Dataset = "/em/fibsem-uint16/s4";
		final int[] blockSize = {32, 32, 32};
		final int scaleSteps = 8;
		final int octaveSteps = 2;
		final double[] resolution = {4, 4, 4};


		/* create the scale space sigma vectors for the Gaussian scale space */
		final double[][][] sigmaSeries = sigmaSeries(resolution, octaveSteps, scaleSteps);

		/* print them */
		for (int i = 0; i < scaleSteps; ++i) {

			System.out.println(
					i + ": " +
					Arrays.toString(sigmaSeries[0][i]) + " : " +
					Arrays.toString(sigmaSeries[1][i]) + " : " +
					Arrays.toString(sigmaSeries[2][i]));
		}

		final N5Reader n5 = new N5Factory().openReader(n5Url);
		final RandomAccessibleInterval<T> img = N5Utils.openVolatile(n5, n5Dataset);

		final RandomAccessibleInterval<DoubleType> converted =
				Converters.convert(
						img,
						(a, b) -> b.set(a.getRealDouble()),
						new DoubleType());

		ExtendedRandomAccessibleInterval<DoubleType, RandomAccessibleInterval<DoubleType>> source =
				Views.extendMirrorSingle(converted);

		/* scale space */
		final ArrayList<RandomAccessibleInterval<DoubleType>> scales = new ArrayList<>();
		for (int i = 0; i < scaleSteps; ++i) {
			final SimpleGaussRA<DoubleType> op = new SimpleGaussRA<>(sigmaSeries[2][i]);
			op.setInput(source);
			final RandomAccessibleInterval<DoubleType> smoothed = Lazy.generate(
					img,
					blockSize,
					new DoubleType(),
					AccessFlags.setOf(),
					op);
			source = Views.extendMirrorSingle(smoothed);
			scales.add(smoothed);
		}

		/* scale space of tubeness */
		final ArrayList<RandomAccessibleInterval<DoubleType>> results = new ArrayList<>();
		for (int i = 1; i < scaleSteps; ++i) {

			/* gradients */
			@SuppressWarnings("unchecked")
			final RandomAccessible<DoubleType>[] gradients = new RandomAccessible[img.numDimensions()];
			for (int d = 0; d < img.numDimensions(); ++d) {
				final GradientCenter<DoubleType> gradientOp =
						new GradientCenter<>(
								Views.extendBorder(scales.get(i)),
								d,
								sigmaSeries[0][i][d]);
				final RandomAccessibleInterval<DoubleType> gradient = Lazy.generate(img, blockSize, new DoubleType(), AccessFlags.setOf(), gradientOp);
				gradients[d] = Views.extendZero(gradient);
			}

			/* tubeness */
			final TubenessCenter<DoubleType> tubenessOp = new TubenessCenter<>(gradients, sigmaSeries[0][i]);
			final RandomAccessibleInterval<DoubleType> tubeness = Lazy.generate(img, blockSize, new DoubleType(), AccessFlags.setOf(AccessFlags.VOLATILE), tubenessOp);
			results.add(tubeness); // works without extension because the size is the same as max output
		}

		/* max project scale space of tubeness */
		final Max<DoubleType> maxOp = new Max<>(results);
		final RandomAccessibleInterval<DoubleType> scaleTubeness = Lazy.generate(img, blockSize, new DoubleType(), AccessFlags.setOf(AccessFlags.VOLATILE), maxOp);

		/* multiply with intensities */
		final Multiply<DoubleType> mulOp = new Multiply<>(scaleTubeness, source);
		final RandomAccessibleInterval<DoubleType> multipliedTubeness = Lazy.generate(scaleTubeness, blockSize, new DoubleType(), AccessFlags.setOf(AccessFlags.VOLATILE), mulOp);

		BdvOptions options = BdvOptions.options();
		for (final RandomAccessibleInterval<DoubleType> result : results) {
			final BdvStackSource<Volatile<DoubleType>> stackSource =
					BdvFunctions.show(
							VolatileViews.wrapAsVolatile(result),
							"",
							options.sourceTransform(resolution));
			stackSource.setDisplayRange(-1, 1);
			options = options.addTo(stackSource);
		}

		BdvStackSource<Volatile<DoubleType>> stackSource =
				BdvFunctions.show(
						VolatileViews.wrapAsVolatile(scaleTubeness),
						"max",
						options.sourceTransform(resolution));
		stackSource.setDisplayRange(-1, 1);
		stackSource =
				BdvFunctions.show(
						VolatileViews.wrapAsVolatile(multipliedTubeness),
						"multitplied",
						options.sourceTransform(resolution));
		stackSource.setDisplayRange(-1, 1);

	}

	/**
	 * Generate a series of n-d sigma vectors to make a Gaussian scale space.
	 *
	 * @param resolution in metric per pixels
	 * @param stepsPerOctave a scale octave doubles the scale/ sigma
	 * @param steps
	 * @return
	 */
	private static double[][][] sigmaSeries(
			final double[] resolution,
			final int stepsPerOctave,
			final int steps) {

		final double factor = Math.pow(2, 1.0 / stepsPerOctave);

		final int n = resolution.length;
		final double[][][] series = new double[3][steps][n];
		final double minRes = Arrays.stream(resolution).min().getAsDouble();

		double targetSigma = 0.5;
		for (int i = 0; i < steps; ++i) {
			for (int d = 0; d < n; ++d) {
				series[0][i][d] = targetSigma / resolution[d] * minRes;
				series[1][i][d] = Math.max(0.5, series[0][i][d]);
			}
			targetSigma *= factor;
		}
		for (int i = 1; i < steps; ++i) {
			for (int d = 0; d < n; ++d) {
				series[2][i][d] = Math.sqrt(Math.max(0, series[1][i][d] * series[1][i][d] - series[1][i - 1][d] * series[1][i - 1][d]));
			}
		}

		return series;
	}
}
