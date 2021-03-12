package org.janelia.saalfeldlab.neubias;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.n5.universe.N5Factory;
import org.janelia.saalfeldlab.n5.universe.Singleton;
import org.janelia.saalfeldlab.neubias.ops.CLLCN;
import org.janelia.saalfeldlab.neubias.ops.ImageJStackOp;
import org.janelia.saalfeldlab.neubias.util.Grid;
import org.janelia.saalfeldlab.neubias.util.Lazy;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.basictypeaccess.AccessFlags;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Util;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "i2k2020-tutorial-spark-1")
public class SparkTutorial1 implements Callable<Void> {

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
			names = {"-o", "--n5outurl"},
			required = true,
			description = "N5 output URL, e.g. '/home/saalfeld/tmp/jrc_hela-2.n5'")
	private String n5OutUrl = null;

	@Option(
			names = {"-e", "--n5outdataset"},
			required = true,
			description = "N5 output dataset, e.g. '/em/fibsem-uint16/s4'")
	private String n5OutDataset = null;

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

		new CommandLine(new SparkTutorial1()).execute(args);
	}

	/**
	 * The real implementation.  We use {@link Callable Callable<Void>} instead
	 * of {@link Runnable} because {@link Runnable#run()} cannot throw
	 * {@link Exception Exceptions}.
	 *
	 * @throws Exception
	 */
	@Override
	public Void call() throws Exception {

		/* create Spark context */
		final SparkConf conf = new SparkConf().setAppName(this.getClass().getName());
		final JavaSparkContext sc = new JavaSparkContext(conf);

		/* get some data about the input */
		final N5Reader n5 = new N5Factory().openReader(n5Url);
		final DatasetAttributes attributes = n5.getDatasetAttributes(n5Dataset);

		/* create the output */
		final N5Writer n5Writer = new N5Factory().openWriter(n5OutUrl);
		n5Writer.createDataset(n5OutDataset, attributes);

		/* create the grid for parallelization */
		final List<long[][]> grid = Grid.create(attributes.getDimensions(), attributes.getBlockSize());

		/* Sparkify it */
		final JavaRDD<long[][]> rddGrid = sc.parallelize(grid);

		final double scale = 1.0 / Math.pow(2, scaleIndex);
		final int blockRadius = (int)Math.round(1023 * scale);

		/* delegate to a method that can be parameterized */
		run(sc, n5Url, n5Dataset, n5OutUrl, n5OutDataset, blockRadius, rddGrid);

		sc.close();

		return null;
	}

	private static final <T extends NativeType<T> & RealType<T>> void run(
			final JavaSparkContext sc,
			final String n5Url,
			final String n5Dataset,
			final String n5OutUrl,
			final String n5OutDataset,
			final int blockRadius,
			final JavaRDD<long[][]> rddGrid) throws IOException {

		final N5Reader n5 = Singleton.get(
				n5Url + ".reader",
				() -> new N5Factory().openReader(n5Url));

//		final N5Reader n5 = new N5Factory().openReader(n5Url);

		final RandomAccessibleInterval<T> img = Singleton.get(
				n5Url + ":" + n5Dataset,
				() -> N5Utils.open(n5, n5Dataset));

		rddGrid.foreach(gridBlock -> {

			final N5Reader n5 = Singleton.get(
					n5Url + ".reader",
					() -> new N5Factory().openReader(n5Url));

//			final N5Reader n5 = new N5Factory().openReader(n5Url);

			final RandomAccessibleInterval<T> img = Singleton.get(
					n5Url + ":" + n5Dataset,
					() -> N5Utils.open(n5, n5Dataset));

//			final RandomAccessibleInterval<T> img = N5Utils.open(n5, n5Dataset);

			/* Use the new ImageJ plugin contrast limited local contrast normalization */
//			final ImageJStackOp<T> cllcn =
//					new ImageJStackOp<>(
//							Views.extendZero(img),
//							(fp) -> new CLLCN(fp).run(blockRadius, blockRadius, 3f, 10, 0.5f, true, true, true),
//							blockRadius,
//							0,
//							65535);

			final ImageJStackOp<T> cllcn = Singleton.get(
					"cllcn",
					() -> new ImageJStackOp<>(
							Views.extendZero(img),
							(fp) -> new CLLCN(fp).run(
									blockRadius,
									blockRadius,
									3f,
									10,
									0.5f,
									true,
									true,
									true),
							blockRadius,
							0,
							65535));

//			final RandomAccessibleInterval<T> cllcned = Lazy.generate(
//					img,
//					new int[] {256, 256, 32},
//					img.randomAccess().get().createVariable(),
//					AccessFlags.setOf(AccessFlags.VOLATILE),
//					cllcn);

			final RandomAccessibleInterval<T> cllcned = Singleton.get(
					"cllcned",
					() -> Lazy.generate(
						img,
						new int[] {256, 256, 32},
						img.randomAccess().get().createVariable(),
						AccessFlags.setOf(AccessFlags.VOLATILE),
						cllcn));

			/* crop the block of interest */
			final IntervalView<T> block = Views.offsetInterval(cllcned, gridBlock[0], gridBlock[1]);

			final N5Writer n5Writer = Singleton.get(
					n5OutUrl + ".writer",
					() -> new N5Factory().openWriter(n5OutUrl));

//			final N5Writer n5Writer = new N5Factory().openWriter(n5OutUrl);

			N5Utils.saveNonEmptyBlock(block, n5Writer, n5OutDataset, gridBlock[2], Util.getTypeFromInterval(block).createVariable());
		});
	}
}
