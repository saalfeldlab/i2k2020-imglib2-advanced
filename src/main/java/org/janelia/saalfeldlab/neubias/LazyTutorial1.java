package org.janelia.saalfeldlab.neubias;

import java.io.IOException;

import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.neubias.ops.CLIJ2FilterOp;
import org.janelia.saalfeldlab.neubias.ops.CLLCN;
import org.janelia.saalfeldlab.neubias.ops.ImageJStackOp;
import org.janelia.saalfeldlab.neubias.util.Lazy;
import org.janelia.saalfeldlab.neubias.util.N5Factory;

import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.util.volatiles.SharedQueue;
import bdv.util.volatiles.VolatileViews;
import ij.ImagePlus;
import mpicbg.ij.clahe.Flat;
import mpicbg.ij.plugin.NormalizeLocalContrast;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converters;
import net.imglib2.img.basictypeaccess.AccessFlags;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import picocli.CommandLine.Command;

@Command(name = "i2k2020-tutorial-lazy-1")
public class LazyTutorial1 {

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

	private static final <T extends NativeType<T> & IntegerType<T>> void run() throws IOException {

		final String n5Url = "https://janelia-cosem.s3.amazonaws.com/jrc_hela-2/jrc_hela-2.n5";
		final String n5Dataset = "/em/fibsem-uint16/s4";
		final int scaleIndex = 4;

		final SharedQueue queue = new SharedQueue(Math.max(1, Runtime.getRuntime().availableProcessors() - 1));

		final N5Reader n5 = N5Factory.openReader(n5Url);
		final RandomAccessibleInterval<T> img = N5Utils.openVolatile(n5, n5Dataset);

		final double scale = 1.0 / Math.pow(2, scaleIndex);
		final int blockRadius = (int)Math.round(1023 * scale);

		/* Use the ImageJ CLAHE plugin */
		final ImageJStackOp<T> clahe =
				new ImageJStackOp<>(
						Views.extendMirrorSingle(img),
						(fp) -> Flat.getFastInstance().run(new ImagePlus("", fp), blockRadius, 256, 2.5f, null, false),
						blockRadius,
						0,
						65535);
		final RandomAccessibleInterval<T> clahed = Lazy.generate(
				img,
				new int[] {256, 256, 32},
				img.randomAccess().get().createVariable(),
				AccessFlags.setOf(AccessFlags.VOLATILE),
				clahe);

		/* Use the ImageJ plugin local contrast normalization */
		final ImageJStackOp<T> lcn =
				new ImageJStackOp<>(
						Views.extendMirrorSingle(img),
						(fp) -> NormalizeLocalContrast.run(fp, blockRadius, blockRadius, 3f, true, true),
						blockRadius,
						0,
						65535);
		final RandomAccessibleInterval<T> lcned = Lazy.generate(
				img,
				new int[] {256, 256, 32},
				img.randomAccess().get().createVariable(),
				AccessFlags.setOf(AccessFlags.VOLATILE),
				lcn);

		/* Use the new ImageJ plugin contrast limited local contrast normalization */
		final ImageJStackOp<T> cllcn =
				new ImageJStackOp<>(
						Views.extendMirrorSingle(img),
						(fp) -> new CLLCN(fp).run(blockRadius, blockRadius, 3f, 10, 0.5f, true, true, true),
						blockRadius,
						0,
						65535);
		final RandomAccessibleInterval<T> cllcned = Lazy.generate(
				img,
				new int[] {256, 256, 32},
				img.randomAccess().get().createVariable(),
				AccessFlags.setOf(AccessFlags.VOLATILE),
				cllcn);

		/* A bit more fun: Invert and float convert the image, then use the CLIJ2 DoG filter */
		final RandomAccessibleInterval<FloatType> inverted =
				Converters.convert(img, (a, b) -> b.setReal(0xffff - a.getRealDouble()), new FloatType());
		final CLIJ2FilterOp<FloatType, FloatType> clij2Filter =
				new CLIJ2FilterOp<>(Views.extendMirrorSingle(inverted), 20, 20, 20);
		clij2Filter.setFilter(
				(a, b) -> clij2Filter.getClij2().differenceOfGaussian(a, b, 4, 4, 4, 3, 3, 3));
		final RandomAccessibleInterval<FloatType> clij2filtered = Lazy.generate(
				img,
				new int[] {128, 128, 128},
				new FloatType(),
				AccessFlags.setOf(AccessFlags.VOLATILE),
				clij2Filter);


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
