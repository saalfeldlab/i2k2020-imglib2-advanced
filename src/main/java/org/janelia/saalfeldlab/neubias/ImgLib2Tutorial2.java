package org.janelia.saalfeldlab.neubias;

import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.viewer.DisplayMode;
import net.imglib2.position.FunctionRealRandomAccessible;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.util.Intervals;
import net.imglib2.view.RandomAccessibleOnRealRandomAccessible;
import net.imglib2.view.Views;

public class ImgLib2Tutorial2 {

	public static void main(final String[] s) throws InterruptedException {

		final double cr = 0.2;
		final double ci = 0.6;

		final FunctionRealRandomAccessible<IntType> juliaSet = new FunctionRealRandomAccessible<>(
				2,
				(x, fx) -> {
					int i = 0;
					double v = 0, c = x.getDoublePosition(0), d = x.getDoublePosition(1);
					while (i < 64 && v < 4096) {
						final double e = c * c - d * d;
						d = 2 * c * d;
						c = e + cr;
						d += ci;
						v = Math.sqrt(c * c + d * d);
						++i;
					}
					fx.set(i);
				},
				IntType::new);

		BdvStackSource<IntType> bdv = BdvFunctions.show(
				juliaSet,
				Intervals.createMinMax(-1, -1, 1, 1),
				"Julia set",
				BdvOptions.options().is2D());
		bdv.setDisplayRange(0, 64);

		final RandomAccessibleOnRealRandomAccessible<IntType> juliaSetImg = Views.raster(juliaSet);

		bdv = BdvFunctions.show(
				Views.interval(juliaSetImg, Intervals.createMinMax(-1, -1, 1, 1)),
				"Julia set rastered and cropped",
				BdvOptions.options().is2D().addTo(bdv));
		bdv.setDisplayRange(0, 64);

		bdv.getBdvHandle().getViewerPanel().state().setDisplayMode(DisplayMode.SINGLE);
	}

}
