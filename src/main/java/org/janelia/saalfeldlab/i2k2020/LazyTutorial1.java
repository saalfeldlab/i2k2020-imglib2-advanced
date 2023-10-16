package org.janelia.saalfeldlab.i2k2020;

import java.io.IOException;
import java.util.concurrent.Callable;

import org.janelia.saalfeldlab.i2k2020.ops.CLIJ2FilterOp;
import org.janelia.saalfeldlab.i2k2020.ops.CLLCN;
import org.janelia.saalfeldlab.i2k2020.ops.ImageJStackOp;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.n5.universe.N5Factory;

import bdv.cache.SharedQueue;
import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.util.volatiles.VolatileViews;
import ij.ImagePlus;
import mpicbg.ij.clahe.Flat;
import mpicbg.ij.plugin.NormalizeLocalContrast;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.img.ReadOnlyCachedCellImgFactory;
import net.imglib2.cache.img.ReadOnlyCachedCellImgOptions;
import net.imglib2.converter.Converters;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "i2k2020-tutorial-lazy-1")
public class LazyTutorial1 implements Callable<Void> {

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
			names = {"-s", "--scaleindex"},
			required = true,
			description = "scale index, e.g. 4")
	private int scaleIndex = 0;

	/**
	 * Start the tool.  We ignore the exit code returned by
	 * {@link CommandLine#execute(String...)} but this can be useful in other
	 * applications.
	 *
	 * @param args
	 */
	public static void main(final String... args) {

		new CommandLine(new LazyTutorial1()).execute(args);
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

	private final <T extends NativeType<T> & IntegerType<T>> void run() throws IOException {

		final SharedQueue queue = new SharedQueue(Math.max(1, Runtime.getRuntime().availableProcessors() - 1));

		final N5Reader n5 = new N5Factory().openReader(n5Url);
		final RandomAccessibleInterval<T> img = N5Utils.openVolatile(n5, n5Dataset);

		final double scale = 1.0 / Math.pow(2, scaleIndex);
		final int blockRadius = (int)Math.round(1023 * scale);

		/* create a cached image factory with reasonable default values */
		final ReadOnlyCachedCellImgFactory cacheFactory = new ReadOnlyCachedCellImgFactory(
				new ReadOnlyCachedCellImgOptions()
						.volatileAccesses(true)				//< use volatile accesses for display
						.cellDimensions(256, 256, 32));		//< standard block size for this example

		/* Use the ImageJ CLAHE plugin in an op to produce CLAHE enhanced cells */
		final ImageJStackOp<T> clahe =
				new ImageJStackOp<>(
						Views.extendMirrorSingle(img),
						(fp) -> Flat.getFastInstance().run(new ImagePlus("", fp), blockRadius, 256, 2.5f, null, false),
						blockRadius,
						0,
						65535);

		/* create a lazy-generating cached cell image using the clahe op as a cell loader */
		final RandomAccessibleInterval<T> clahed = cacheFactory.create(
				img.dimensionsAsLongArray(),				//< the size of the result
				img.randomAccess().get().createVariable(),	//< the type that is used to generate the result pixels
				clahe::accept);								//< the consumer that creates each cell

		/* Use the ImageJ plugin local contrast normalization in an op to produce contrast enhanced cells */
		final ImageJStackOp<T> lcn =
				new ImageJStackOp<>(
						Views.extendMirrorSingle(img),
						(fp) -> NormalizeLocalContrast.run(fp, blockRadius, blockRadius, 3f, true, true),
						blockRadius,
						0,
						65535);

		/* create a lazy-generating cached cell image using the lcn op as a cell loader */
		final RandomAccessibleInterval<T> lcned = cacheFactory.create(
				img.dimensionsAsLongArray(),				//< the size of the result
				img.randomAccess().get().createVariable(),	//< the type that is used to generate the result pixels
				lcn::accept);								//< the consumer that creates each cell


		/* Use the new ImageJ plugin contrast limited local contrast normalization in an op to produce contrast enhanced cells */
		final ImageJStackOp<T> cllcn =
				new ImageJStackOp<>(
						Views.extendMirrorSingle(img),
						(fp) -> new CLLCN(fp).run(blockRadius, blockRadius, 3f, 10, 0.5f, true, true, true),
						blockRadius,
						0,
						65535);

		/* create a lazy-generating cached cell image using the cllcn op as a cell loader */
		final RandomAccessibleInterval<T> cllcned = cacheFactory.create(
				img.dimensionsAsLongArray(),				//< the size of the result
				img.randomAccess().get().createVariable(),	//< the type that is used to generate the result pixels
				cllcn::accept);								//< the consumer that creates each cell


		/* A bit more fun: Invert and float convert the image, then use the CLIJ2 DoG filter */
		final RandomAccessibleInterval<FloatType> inverted =
				Converters.convert(img, (a, b) -> b.setReal(0xffff - a.getRealDouble()), new FloatType());
		final CLIJ2FilterOp<FloatType, FloatType> clij2Filter =
				new CLIJ2FilterOp<>(Views.extendMirrorSingle(inverted), 20, 20, 20);
		clij2Filter.setFilter(
				(a, b) -> clij2Filter.getClij2().differenceOfGaussian(a, b, 4, 4, 4, 3, 3, 3));
		final RandomAccessibleInterval<FloatType> clij2filtered = cacheFactory.create(
				img.dimensionsAsLongArray(),				//< the size of the result
				new FloatType(),							//< the type that is used to generate the result pixels
				clij2Filter::accept,
				new ReadOnlyCachedCellImgOptions()
					.cellDimensions(64, 64, 64));						//< the consumer that creates each cell


		/* show it */
		BdvStackSource<?> bdv = null;

		bdv = BdvFunctions.show(
				VolatileViews.wrapAsVolatile(
						img,
						queue),
				n5Dataset,
				BdvOptions.options());
		bdv.setDisplayRange(10000, 50000);

		bdv = BdvFunctions.show(
				VolatileViews.wrapAsVolatile(
						clahed,
						queue),
				n5Dataset + " CLAHE",
				BdvOptions.options().addTo(bdv));
		bdv.setDisplayRange(10000, 50000);

		bdv = BdvFunctions.show(
				VolatileViews.wrapAsVolatile(
						lcned,
						queue),
				n5Dataset + " LCN",
				BdvOptions.options().addTo(bdv));
		bdv.setDisplayRange(10000, 50000);

		bdv = BdvFunctions.show(
				VolatileViews.wrapAsVolatile(
						cllcned,
						queue),
				n5Dataset + " CLLCN",
				BdvOptions.options().addTo(bdv));
		bdv.setDisplayRange(10000, 50000);

		bdv = BdvFunctions.show(
				VolatileViews.wrapAsVolatile(
						clij2filtered,
						queue),
				n5Dataset + " CLIJ2 DoG",
				BdvOptions.options().addTo(bdv));
		bdv.setDisplayRange(-1000, 1000);
	}
}
