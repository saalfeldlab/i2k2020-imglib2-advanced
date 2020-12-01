package org.janelia.saalfeldlab.i2k2020;

import java.io.IOException;
import java.util.concurrent.Callable;

import org.janelia.saalfeldlab.i2k2020.ops.CLLCN;
import org.janelia.saalfeldlab.i2k2020.ops.ImageJStackOp;
import org.janelia.saalfeldlab.i2k2020.util.Lazy;
import org.janelia.saalfeldlab.i2k2020.util.N5Factory;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;

import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.util.volatiles.SharedQueue;
import bdv.util.volatiles.VolatileViews;
import ij.ImagePlus;
import mpicbg.ij.clahe.Flat;
import mpicbg.ij.plugin.NormalizeLocalContrast;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.basictypeaccess.AccessFlags;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.view.Views;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "i2k2020-tutorial-lazy-1")
public class LazyTutorial1 implements Callable<Void> {

	@Option(
			names = {"-i", "--n5url"},
			required = false,
			description = "N5 URL, e.g. 'https://janelia-cosem.s3.amazonaws.com/jrc_hela-2/jrc_hela-2.n5'")
	private String n5Url = "https://janelia-cosem.s3.amazonaws.com/jrc_hela-2/jrc_hela-2.n5";

	@Option(
			names = {"-d", "--n5dataset"},
			required = false,
			description = "N5 dataset, e.g. '/em/fibsem-uint16/s4'")
	private String n5Dataset = "/em/fibsem-uint16/s2";

	@Option(
			names = {"-s", "--scaleindex"},
			required = false,
			description = "scale index, e.g. 4")
	private int scaleIndex = 4;

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

		final SharedQueue queue = new SharedQueue(Math.max(1, Runtime.getRuntime().availableProcessors() / 2));

		final double scale = 1.0 / Math.pow(2, scaleIndex);
		final N5Reader n5 = N5Factory.openAWSS3Reader(n5Url);
		final RandomAccessibleInterval<T> img = N5Utils.openVolatile(n5, n5Dataset);

		final int blockRadius = (int)Math.round(1023 * scale);

		/* Use the ImageJ CLAHE plugin */
		final ImageJStackOp<T> clahe =
				new ImageJStackOp<>(
						Views.extendZero(img),
						(fp) -> Flat.getFastInstance().run(new ImagePlus("", fp), blockRadius, 256, 2.5f, null, false),
						blockRadius,
						0,
						65535);
		final RandomAccessibleInterval<T> clahed = Lazy.process(
				img,
				new int[] {256, 256, 32},
				img.randomAccess().get().createVariable(),
				AccessFlags.setOf(AccessFlags.VOLATILE),
				clahe);

		/* Use the ImageJ plugin local contrast normalization */
		final ImageJStackOp<T> lcn =
				new ImageJStackOp<>(
						Views.extendZero(img),
						(fp) -> NormalizeLocalContrast.run(fp, blockRadius, blockRadius, 3f, true, true),
						blockRadius,
						0,
						65535);
		final RandomAccessibleInterval<T> lcned = Lazy.process(
				img,
				new int[] {256, 256, 32},
				img.randomAccess().get().createVariable(),
				AccessFlags.setOf(AccessFlags.VOLATILE),
				lcn);

		/* Use the new ImageJ plugin contrast limited local contrast normalization */
		final ImageJStackOp<T> cllcn =
				new ImageJStackOp<>(
						Views.extendZero(img),
						(fp) -> new CLLCN(fp).run(blockRadius, blockRadius, 3f, 10, 0.5f, true, true, true),
						blockRadius,
						0,
						65535);
		final RandomAccessibleInterval<T> cllcned = Lazy.process(
				img,
				new int[] {256, 256, 32},
				img.randomAccess().get().createVariable(),
				AccessFlags.setOf(AccessFlags.VOLATILE),
				cllcn);


		/* show it */
		BdvStackSource<?> bdv = null;

		bdv = BdvFunctions.show(
				VolatileViews.wrapAsVolatile(
						img,
						queue),
				n5Dataset,
				BdvOptions.options());

		bdv = BdvFunctions.show(
				VolatileViews.wrapAsVolatile(
						clahed,
						queue),
				n5Dataset + " CLAHE",
				BdvOptions.options().addTo(bdv));

		bdv = BdvFunctions.show(
				VolatileViews.wrapAsVolatile(
						lcned,
						queue),
				n5Dataset + " LCN",
				BdvOptions.options().addTo(bdv));

		bdv = BdvFunctions.show(
				VolatileViews.wrapAsVolatile(
						cllcned,
						queue),
				n5Dataset + " CLLCN",
				BdvOptions.options().addTo(bdv));
	}
}
