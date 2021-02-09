package org.janelia.saalfeldlab.neubias;

import java.io.IOException;

import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.neubias.ops.CLIJ2FilterOp;
import org.janelia.saalfeldlab.neubias.util.Lazy;
import org.janelia.saalfeldlab.neubias.util.N5Factory;

import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.util.volatiles.SharedQueue;
import bdv.util.volatiles.VolatileViews;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converters;
import net.imglib2.img.basictypeaccess.AccessFlags;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;

public class LazyTutorial3 {

	public static final void main(final String... args) throws IOException {

		final String n5Url = "https://janelia-cosem.s3.amazonaws.com/jrc_hela-2/jrc_hela-2.n5";
		final String n5Dataset = "/em/fibsem-uint16/s0";

		final N5Reader n5 = N5Factory.openReader(n5Url);
		final RandomAccessibleInterval<UnsignedShortType> img = N5Utils.openVolatile(n5, n5Dataset);

		final SharedQueue queue = new SharedQueue(Math.max(1, Runtime.getRuntime().availableProcessors() - 1));
		BdvStackSource<?> bdv = BdvFunctions.show(VolatileViews.wrapAsVolatile(img, queue), "source");
		bdv.setDisplayRange(24000, 32000);

		final RandomAccessibleInterval<FloatType> floats =
				Converters.convert(
						img,
						(a, b) -> b.set(a.getRealFloat()),
						new FloatType());

		final CLIJ2FilterOp<FloatType, FloatType> clij2Filter =
				new CLIJ2FilterOp<>(Views.extendMirrorSingle(floats), 20, 20, 20);
		clij2Filter.setFilter(
				(a, b) -> clij2Filter.getClij2().differenceOfGaussian(a, b, 4, 4, 4, 3, 3, 3));
		final RandomAccessibleInterval<FloatType> filtered = Lazy.generate(
				img,
				new int[] {128, 128, 128},
				new FloatType(),
				AccessFlags.setOf(AccessFlags.VOLATILE),
				clij2Filter);

		bdv = BdvFunctions.show(VolatileViews.wrapAsVolatile(filtered, queue), "DoG", BdvOptions.options().addTo(bdv));
		bdv.setDisplayRange(-1000, 1000);
	}
}
