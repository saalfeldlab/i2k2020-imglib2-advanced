package org.janelia.saalfeldlab.i2k2020;

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaSparkContext;
import org.janelia.saalfeldlab.i2k2020.util.N5Factory;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5FSWriter;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.spark.downsample.N5DownsamplerSpark;
import org.janelia.saalfeldlab.n5.spark.supplier.N5WriterSupplier;
import org.janelia.saalfeldlab.n5.spark.util.CmdUtils;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

public class N5ScalePyramidSparkExample
{
	/**
	 * Generates a scale pyramid for a given dataset. Each scale level is downsampled by the specified factors.
	 * Reuses the block size of the input dataset. Stores the resulting datasets in the same group as the input dataset.
	 *
	 * @param sparkContext
	 * @param n5Supplier
	 * @param datasetPath
	 * @param downsamplingStepFactors
	 * @return N5 paths to downsampled datasets
	 * @throws IOException
	 */
	public static List< String > downsampleScalePyramid(
			final JavaSparkContext sparkContext,
			final N5WriterSupplier n5Supplier,
			final String datasetPath,
			final int[] downsamplingStepFactors ) throws IOException
	{
		final String outputGroupPath = ( Paths.get( datasetPath ).getParent() != null ? Paths.get( datasetPath ).getParent().toString() : "" );
		return downsampleScalePyramid(
				sparkContext,
				n5Supplier,
				datasetPath,
				outputGroupPath,
				downsamplingStepFactors
			);
	}

	/**
	 * Generates a scale pyramid for a given dataset. Each scale level is downsampled by the specified factors.
	 * Reuses the block size of the input dataset. Stores the resulting datasets in the given output group.
	 *
	 * @param sparkContext
	 * @param n5Supplier
	 * @param datasetPath
	 * @param outputGroupPath
	 * @param downsamplingStepFactors
	 * @return N5 paths to downsampled datasets
	 * @throws IOException
	 */
	public static List< String > downsampleScalePyramid(
			final JavaSparkContext sparkContext,
			final N5WriterSupplier n5Supplier,
			final String datasetPath,
			final String outputGroupPath,
			final int[] downsamplingStepFactors ) throws IOException
	{
		final N5Writer n5 = n5Supplier.get();
		final DatasetAttributes fullScaleAttributes = n5.getDatasetAttributes( datasetPath );
		final long[] dimensions = fullScaleAttributes.getDimensions();
		final int dim = dimensions.length;

		final List< String > downsampledDatasets = new ArrayList<>();

		for ( int scale = 1; ; ++scale )
		{
			final int[] scaleFactors = new int[ dim ];
			for ( int d = 0; d < dim; ++d )
				scaleFactors[ d ] = ( int ) Math.round( Math.pow( downsamplingStepFactors[ d ], scale ) );

			final long[] downsampledDimensions = new long[ dim ];
			for ( int d = 0; d < dim; ++d )
				downsampledDimensions[ d ] = dimensions[ d ] / scaleFactors[ d ];

			if ( Arrays.stream( downsampledDimensions ).min().getAsLong() < 1 )
				break;

			final String inputDatasetPath = scale == 1 ? datasetPath : Paths.get( outputGroupPath, "s" + ( scale - 1 ) ).toString();
			final String outputDatasetPath = Paths.get( outputGroupPath, "s" + scale ).toString();

			N5DownsamplerSpark.downsample(
					sparkContext,
					n5Supplier,
					inputDatasetPath,
					outputDatasetPath,
					downsamplingStepFactors
				);

			downsampledDatasets.add( outputDatasetPath );
		}

		return downsampledDatasets;
	}


	public static void main( final String... args ) throws IOException
	{
		final Arguments parsedArgs = new Arguments( args );

		try ( final JavaSparkContext sparkContext = new JavaSparkContext( new SparkConf()
				.setAppName( "N5ScalePyramidSpark" )
				.set( "spark.serializer", "org.apache.spark.serializer.KryoSerializer" )
			) )
		{

			final N5WriterSupplier n5Supplier = () -> {
				return N5Factory.openWriter( parsedArgs.getN5Path() );
			};

			if ( parsedArgs.getOutputGroupPath() != null )
			{
				downsampleScalePyramid(
						sparkContext,
						n5Supplier,
						parsedArgs.getInputDatasetPath(),
						parsedArgs.getOutputGroupPath(),
						parsedArgs.getDownsamplingFactors()
					);
			}
			else
			{
				downsampleScalePyramid(
						sparkContext,
						n5Supplier,
						parsedArgs.getInputDatasetPath(),
						parsedArgs.getDownsamplingFactors()
					);
			}
		}
	}

	private static class Arguments implements Serializable
	{
		private static final long serialVersionUID = -1467734459169624759L;

		@Option(name = "-n", aliases = { "--n5Path" }, required = true,
				usage = "Path to an N5 container.")
		private String n5Path;

		@Option(name = "-i", aliases = { "--inputDatasetPath" }, required = true,
				usage = "Path to an input dataset within the N5 container (e.g. data/group/s0).")
		private String inputDatasetPath;

		@Option(name = "-o", aliases = { "--outputGroupPath" }, required = false,
				usage = "Path to a group within the N5 container to store the output datasets (e.g. data/group/scale-pyramid).")
		private String outputGroupPath;

		@Option(name = "-f", aliases = { "--factors" }, required = true,
				usage = "Downsampling factors.")
		private String downsamplingFactors;

		public Arguments( final String... args ) throws IllegalArgumentException
		{
			final CmdLineParser parser = new CmdLineParser( this );
			try
			{
				parser.parseArgument( args );
			}
			catch ( final CmdLineException e )
			{
				System.err.println( e.getMessage() );
				parser.printUsage( System.err );
				System.exit( 1 );
			}
		}

		public String getN5Path() { return n5Path; }
		public String getInputDatasetPath() { return inputDatasetPath; }
		public String getOutputGroupPath() { return outputGroupPath; }
		public int[] getDownsamplingFactors() { return CmdUtils.parseIntArray( downsamplingFactors ); }
	}
}
