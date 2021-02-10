package org.janelia.saalfeldlab.neubias;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaSparkContext;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.spark.downsample.N5DownsamplerSpark;
import org.janelia.saalfeldlab.n5.spark.supplier.N5WriterSupplier;
import org.janelia.saalfeldlab.neubias.util.N5Factory;

import picocli.CommandLine;
import picocli.CommandLine.Option;

public class SparkTutorial3 implements Callable<Void> {

	@Option(
			names = {"-n", "--n5Path"},
			required = true,
			description = "Path to an N5 container.")
	private String n5Path = null;

	@Option(
			names = {"-i", "--inputDatasetPath"},
			required = true,
			description = "Path to an input dataset within the N5 container (e.g. data/group/s0).")
	private String inputDatasetPath = null;

	@Option(
			names = {"-o", "--outputGroupPath"},
			required = false,
			description = "Path to a group within the N5 container to store the output datasets (e.g. data/group/scale-pyramid).")
	private String outputGroupPath = null;

	@Option(
			names = {"-f", "--factors"},
			required = true,
			split = ",",
			description = "Downsampling factors.")
	private int[] downsamplingFactors = null;

	/**
	 * Generates a scale pyramid for a given dataset. Each scale level is
	 * downsampled by the specified factors. Reuses the block size of the input
	 * dataset. Stores the resulting datasets in the same group as the input
	 * dataset.
	 *
	 * @param sparkContext
	 * @param n5Supplier
	 * @param datasetPath
	 * @param downsamplingStepFactors
	 * @return N5 paths to downsampled datasets
	 * @throws IOException
	 */
	public static List<String> downsampleScalePyramid(
			final JavaSparkContext sparkContext,
			final N5WriterSupplier n5Supplier,
			final String datasetPath,
			final int[] downsamplingStepFactors) throws IOException {

		final String outputGroupPath = (Paths.get(datasetPath).getParent() != null ? Paths.get(datasetPath).getParent().toString() : "");
		return downsampleScalePyramid(
				sparkContext,
				n5Supplier,
				datasetPath,
				outputGroupPath,
				downsamplingStepFactors);
	}

	/**
	 * Generates a scale pyramid for a given dataset. Each scale level is
	 * downsampled by the specified factors. Reuses the block size of the input
	 * dataset. Stores the resulting datasets in the given output group.
	 *
	 * @param sparkContext
	 * @param n5Supplier
	 * @param datasetPath
	 * @param outputGroupPath
	 * @param downsamplingStepFactors
	 * @return N5 paths to downsampled datasets
	 * @throws IOException
	 */
	public static List<String> downsampleScalePyramid(
			final JavaSparkContext sparkContext,
			final N5WriterSupplier n5Supplier,
			final String datasetPath,
			final String outputGroupPath,
			final int[] downsamplingStepFactors) throws IOException {

		final N5Writer n5 = n5Supplier.get();
		final DatasetAttributes fullScaleAttributes = n5.getDatasetAttributes(datasetPath);
		final long[] dimensions = fullScaleAttributes.getDimensions();
		final int dim = dimensions.length;

		final List<String> downsampledDatasets = new ArrayList<>();

		for (int scale = 1;; ++scale) {
			final int[] scaleFactors = new int[dim];
			for (int d = 0; d < dim; ++d)
				scaleFactors[d] = (int)Math.round(Math.pow(downsamplingStepFactors[d], scale));

			final long[] downsampledDimensions = new long[dim];
			for (int d = 0; d < dim; ++d)
				downsampledDimensions[d] = dimensions[d] / scaleFactors[d];

			if (Arrays.stream(downsampledDimensions).min().getAsLong() < 1)
				break;

			final String inputDatasetPath = scale == 1 ? datasetPath : Paths.get(outputGroupPath, "s" + (scale - 1)).toString();
			final String outputDatasetPath = Paths.get(outputGroupPath, "s" + scale).toString();

			N5DownsamplerSpark.downsample(
					sparkContext,
					n5Supplier,
					inputDatasetPath,
					outputDatasetPath,
					downsamplingStepFactors);

			downsampledDatasets.add(outputDatasetPath);
		}

		return downsampledDatasets;
	}

	/**
	 * Start the tool. We ignore the exit code returned by
	 * {@link CommandLine#execute(String...)} but this can be useful in other
	 * applications.
	 *
	 * @param args
	 */
	public static void main(final String... args) {

		new CommandLine(new SparkTutorial1()).execute(args);
	}

	/**
	 * The real implementation. We use {@link Callable Callable<Void>} instead
	 * of {@link Runnable} because {@link Runnable#run()} cannot throw
	 * {@link Exception Exceptions}.
	 *
	 * @throws Exception
	 */
	@Override
	public Void call() throws Exception {

		try (final JavaSparkContext sparkContext = new JavaSparkContext(new SparkConf()
				.setAppName("N5ScalePyramidSpark")
				.set("spark.serializer", "org.apache.spark.serializer.KryoSerializer"))) {

			final N5WriterSupplier n5Supplier = () -> {
				return new N5Factory().openWriter(n5Path);
			};

			if (outputGroupPath != null) {
				downsampleScalePyramid(
						sparkContext,
						n5Supplier,
						inputDatasetPath,
						outputGroupPath,
						downsamplingFactors);
			} else {
				downsampleScalePyramid(
						sparkContext,
						n5Supplier,
						inputDatasetPath,
						downsamplingFactors);
			}
		}

		return null;
	}
}
